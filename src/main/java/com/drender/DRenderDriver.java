package com.drender;

import com.drender.cloud.ImageFactory;
import com.drender.eventprocessors.DRenderLogger;
import com.drender.eventprocessors.HeartbeatVerticle;
import com.drender.eventprocessors.ResourceManager;
import com.drender.model.*;
import com.drender.model.cloud.S3Source;
import com.drender.model.instance.DRenderInstance;
import com.drender.model.job.Job;
import com.drender.model.job.JobAction;
import com.drender.model.instance.InstanceRequest;
import com.drender.model.instance.InstanceResponse;
import com.drender.model.project.Project;
import com.drender.model.project.ProjectRequest;
import com.drender.model.project.ProjectResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class DRenderDriver extends AbstractVerticle {

    private final int FRAMES_PER_MACHINE = 20;
    private final int HEARTBEAT_TIMER = 150000;

    // Stores project -> {jobID, Job} mapping
    private Map<Project, Map<String, Job>> projectJobs;
    // Stores project -> {jobID, heartbeatTimerID} mapping
    private Map<Project, Map<String, Long>> projectTimers;

    private Logger logger = LoggerFactory.getLogger(DRenderDriver.class);

    public DRenderDriver(){
        projectJobs = new HashMap<>();
        projectTimers = new HashMap<>();
    }

    @Override
    public void start() throws Exception {

        logger.info("Starting...");

        // Deploy all the verticles
        vertx.deployVerticle(new DRenderLogger());
        vertx.deployVerticle(new HeartbeatVerticle());
        vertx.deployVerticle(new ResourceManager(), new DeploymentOptions().setMaxWorkerExecuteTime(5* 60L * 1000 * 1000000));

        // setup listeners for dRender Driver
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.DRIVER_PROJECT)
                .handler(message -> {
                    ProjectRequest projectRequest = Json.decodeValue(message.body().toString(), ProjectRequest.class);
                    switch (projectRequest.getAction()) {
                        case START:
                            System.out.println("START");
                            vertx.executeBlocking(future -> {
                                startProject(projectRequest)
                                        .setHandler(ar -> {
                                            if (ar.succeeded()) {
                                                message.reply(Json.encode(ar.result()));
                                            } else {
                                                message.reply(Json.encode(ar.cause()));
                                            }
                                        });
                            }, result -> {
                                // Nothing needs to be done here
                            });
                            break;
                        case STATUS:
                        default:
                            getStatus();
                    }
                });
    }

    /**
     * Function to drive the entire process of starting a new project and scheduling new tasks.
     * 1. Initialize project parameters
     * 2. Prepare jobs based on the total number of frames to render
     * 3. Spawn machines based on number of jobs
     * 4. Create output directory in object storage (Example - S3)
     * 5. Update jobs with the newly retrieved IPs
     * 6. Schedule Heartbeat tasks for each of the machines (Jobs)
     * 7. Start jobs in each machine
     * 8. Return response with all the current information
     * @param projectRequest
     * @return
     */
    private Future<ProjectResponse> startProject(ProjectRequest projectRequest) {
        logger.info("Received new request: " + Json.encode(projectRequest));

        Project project = initProjectParameters(projectRequest);

        String cloudAMI = ImageFactory.getImageAMI(project.getSoftware());
        prepareJobs(project);

        List<Job> jobList = new ArrayList<>(projectJobs.get(project).values());

        Future<List<DRenderInstance>> instancesFuture = spawnMachines(cloudAMI, jobList);
        Future<S3Source> outputURIFuture = getOutputSource(project);
        Future<ProjectResponse> projectResponseFuture = Future.future();

        CompositeFuture.all(instancesFuture, outputURIFuture)
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        List<DRenderInstance> instances = instancesFuture.result();
                        S3Source outputURI = outputURIFuture.result();
                        // Updates each job with newly retrieved IPs and outputURI
                        updateJobs(project, instances, outputURI);
                        // Schedule heartbeat checks for the newly created jobs
                            /*for (Job job : projectJobs.get(project).values()) {
                                job.setAction(JobAction.HEARTBEAT_CHECK);
                                long timerID = scheduleHeartbeat(job);
                                // update timer map
                                projectTimers.get(project).put(job.getID(), timerID);
                            }*/

                                            // Start jobs
//                            for (Job job : projectJobs.get(project).values()) {
//                                job.setAction(JobAction.START_JOB);
//                                startJob(job);
//                            }
                        ProjectResponse projectResponse =
                                ProjectResponse.builder()
                                .id("1")
                                .startFrame(project.getStartFrame())
                                .endFrame(project.getEndFrame())
                                .outputURI("").build();
                        projectResponseFuture.complete(projectResponse);

                    } else {
                        logger.error("Could not create instances or output bucket");
                        projectResponseFuture.fail(new Exception());
                    }
                });

        return projectResponseFuture;
    }

    private Project initProjectParameters(ProjectRequest projectRequest) {
        Project project = Project.builder()
                            .ID(projectRequest.getId())
                            .source(projectRequest.getSource())
                            .startFrame(projectRequest.getStartFrame())
                            .endFrame(projectRequest.getEndFrame())
                            .software(projectRequest.getSoftware())
                            .source(projectRequest.getSource())
                            .build();

        // update project map
        Map<String, Job> jobMap = prepareJobs(project);
        projectJobs.put(project, jobMap);

        return project;
    }

    private int getMachineCount(Project project) {
        return projectJobs.get(project).keySet().size();
    }

    private void updateJobs(Project project, List<DRenderInstance> instances, S3Source outputURI) {
        Map<String, Job> jobMap = projectJobs.get(project);

        int instanceIdx = 0;

        for (Job job : jobMap.values()) {
            job.setInstance(instances.get(instanceIdx));
            job.setOutputURI(outputURI);
        }
    }

    private Map<String, Job> prepareJobs(Project project) {
        int currentFrame = project.getStartFrame();
        Map<String, Job> jobMap = new HashMap<>();

        while (currentFrame < project.getEndFrame()) {
            int startFrame = currentFrame;
            int endFrame = (project.getEndFrame() - currentFrame) >= FRAMES_PER_MACHINE ? (currentFrame+FRAMES_PER_MACHINE) : project.getEndFrame();

            Job job = Job.builder()
                        .startFrame(startFrame)
                        .endFrame(endFrame)
                        .projectID(project.getID())
                        .source(project.getSource())
                        .action(JobAction.START_NEW_MACHINE)
                        .build();

            jobMap.put(job.getID(), job);

            currentFrame += FRAMES_PER_MACHINE;
        }

        return jobMap;
    }

    private Future<List<DRenderInstance>> spawnMachines(String cloudAMI, List<Job> jobs) {
        EventBus eventBus = vertx.eventBus();
        InstanceRequest instanceRequest = new InstanceRequest(cloudAMI, jobs);

        final Future<List<DRenderInstance>> ips = Future.future();
        final long TIMEOUT = 5 * 60 * 1000; // 5 minutes (in ms)

        eventBus.send(Channels.INSTANCE_MANAGER, Json.encode(instanceRequest), new DeliveryOptions().setSendTimeout(TIMEOUT),
            ar -> {
                if (ar.succeeded()) {
                    InstanceResponse response = Json.decodeValue(ar.result().body().toString(), InstanceResponse.class);
                    ips.complete(response.getInstances());
                } else {
                    logger.error("Failed to spawn machines: " + ar.cause());
                }
            }
        );

        return ips;
    }

    private Future<S3Source> getOutputSource(Project project) {
        EventBus eventBus = vertx.eventBus();

        final Future<S3Source> future = Future.future();

        eventBus.send(Channels.STORAGE_MANAGER, project.getID(),
            ar -> {
                if (ar.succeeded()) {
                    future.complete(Json.decodeValue(ar.result().body().toString(), S3Source.class));
                }
            }
        );

        return future;
    }

    private long scheduleHeartbeat(Job job) {
        long timerId = vertx.setPeriodic(HEARTBEAT_TIMER, id -> {
            EventBus eventBus = vertx.eventBus();
            eventBus.send(Channels.HEARTBEAT, Json.encode(job));
        });

        return timerId;
    }

    private void startJob(Job job) {
        EventBus eventBus = vertx.eventBus();
        eventBus.send(Channels.JOB_MANAGER, Json.encode(job),
            ar -> {
                if (ar.succeeded()) {
                    System.out.println("DRenderDriver: " + ar.result().body());
                }
            }
        );
    }

    private ProjectResponse getStatus() {
        return ProjectResponse.builder()
                .log(new JsonObject("{\"message\" : \"All good\" }"))
                .build();
    }
}
