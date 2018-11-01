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

    // Stores projectID ->  project mapping
    private Map<String, Project> projects;
    // Stores projectID -> {jobID, Job} mapping
    private Map<String, Map<String, Job>> projectJobs;
    // Stores projectID -> {jobID, heartbeatTimerID} mapping
    private Map<String, Map<String, Long>> projectTimers;
    // Stores projectID -> {jobID, frameSet} mapping
    private Map<String, Map<String, Set<Integer>>> projectJobsFrames;

    private Logger logger = LoggerFactory.getLogger(DRenderDriver.class);

    public DRenderDriver(){
        projectJobs = new HashMap<>();
        projectTimers = new HashMap<>();
        projectJobsFrames = new HashMap<>();
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
                        /*
                         * START is run in worker (executor) threads by vertx as a blocking operation.
                         * This is done as spawning of instances takes time. This prevents the blocking of event loop.
                         */
                        case START:
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
                            message.reply(Json.encode(getStatus(projectRequest.getId())));
                            break;
                        default:
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
        projects.put(project.getID(), project);

        String cloudAMI = ImageFactory.getImageAMI(project.getSoftware());
        prepareJobs(project);

        List<Job> jobList = new ArrayList<>(projectJobs.get(project.getID()).values());

        Future<List<DRenderInstance>> instancesFuture = spawnMachines(cloudAMI, jobList);
        Future<S3Source> outputURIFuture = getOutputSource(project);
        Future<ProjectResponse> projectResponseFuture = Future.future();

        CompositeFuture.all(instancesFuture, outputURIFuture)
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        List<DRenderInstance> instances = instancesFuture.result();
                        S3Source outputURI = outputURIFuture.result();
                        project.setOutputURI(outputURI);
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

                        projectResponseFuture.complete(buildStatus(project));

                    } else {
                        logger.error("Could not create instances or output bucket");
                        projectResponseFuture.fail(new Exception());
                    }
                });

        return projectResponseFuture;
    }

    /**
     * Initializes project, generates jobs and updates the project->job map
     * @param projectRequest
     * @return
     */
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
        projectJobs.put(project.getID(), jobMap);

        return project;
    }

    /**
     * Constructs log object for this project. This is used to construct
     * current status of the jobs in the project.
     * @param project
     * @return
     */
    private JsonObject constructLog(Project project) {
        JsonObject log = new JsonObject();
        List<JsonObject> jobs = new ArrayList<>();

        for (Job job : projectJobs.get(project.getID()).values()) {
            jobs.add(
                new JsonObject()
                .put("id", job.getID())
                .put("startFrame", job.getStartFrame())
                .put("endFrame", job.getEndFrame())
                .put("instanceInfo", job.getInstance())
                .put("framesRendered", projectJobsFrames.get(project.getID()).get(job.getID()).size())
            );
        }
        log.put("jobs", jobs);
        return log;
    }

    private int getMachineCount(Project project) {
        return projectJobs.get(project.getID()).keySet().size();
    }

    /**
     * Assigns jobs to instances, and also updates S3 output path
     * @param project
     * @param instances
     * @param outputURI
     */
    private void updateJobs(Project project, List<DRenderInstance> instances, S3Source outputURI) {
        Map<String, Job> jobMap = projectJobs.get(project.getID());

        int instanceIdx = 0;

        for (Job job : jobMap.values()) {
            job.setInstance(instances.get(instanceIdx));
            job.setOutputURI(outputURI);
        }
    }

    /**
     * Prepares Jobs based on the number of frames to be rendered.
     * Uses static FRAMES_PER_MACHINE to divide frames among Jobs.
     * Sets the job action to START_NEW_MACHINE
     * @param project
     * @return
     */
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

    /**
     * Asynchronously spawns machines. Number of machines spawned is equal to number of jobs to run.
     * Communicates with ResourceManager through messages in the event queue.
     * Callback returns the instances to caller once complete.
     * @param cloudAMI
     * @param jobs
     * @return
     */
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

    /**
     * Asynchronously creates a bucket in S3 for the project.
     * @param project
     * @return
     */
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

    /**
     * Schedules timed heartbeat checks for the given Job.
     * Does not expect a reply, since if the machine runs okay, nothing needs to be done.
     * @param job
     * @return
     */
    private long scheduleHeartbeat(Job job) {
        long timerId = vertx.setPeriodic(HEARTBEAT_TIMER, id -> {
            EventBus eventBus = vertx.eventBus();
            eventBus.send(Channels.HEARTBEAT, Json.encode(job));
        });

        return timerId;
    }

    /**
     * Asynchronously starts the job in the machine associated with the job.
     * Sends START_JOB message to JobManager. 
     * @param job
     */
    private void startJob(Job job) {
        EventBus eventBus = vertx.eventBus();
        eventBus.send(Channels.JOB_MANAGER, Json.encode(job),
            ar -> {
                if (ar.succeeded()) {
                    logger.info("Started Job: " + ar.result().body());
                } else {
                    logger.error("Could not start Job: " + ar.cause());
                }
            }
        );
    }

    private ProjectResponse buildStatus(Project project) {
        return ProjectResponse.builder()
                        .id(project.getID())
                        .source(project.getSource())
                        .startFrame(project.getStartFrame())
                        .endFrame(project.getEndFrame())
                        .software(project.getSoftware())
                        .outputURI(project.getOutputURI())
                        .log(constructLog(project)).build();
    }

    /**
     * Returns current status of the project.
     * Includes logs of the jobs running currently, and their statuses
     * @param projectID
     * @return
     */
    private ProjectResponse getStatus(String projectID) {
        return buildStatus(projects.get(projectID));
    }
}
