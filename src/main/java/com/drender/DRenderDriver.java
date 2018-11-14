package com.drender;

import com.drender.cloud.ImageFactory;
import com.drender.eventprocessors.HeartbeatVerticle;
import com.drender.eventprocessors.JobManager;
import com.drender.eventprocessors.ResourceManager;
import com.drender.model.Channels;
import com.drender.model.cloud.S3Source;
import com.drender.model.instance.*;
import com.drender.model.job.Job;
import com.drender.model.job.JobAction;
import com.drender.model.job.JobFrame;
import com.drender.model.job.MessageQ;
import com.drender.model.project.Project;
import com.drender.model.project.ProjectRequest;
import com.drender.model.project.ProjectResponse;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DRenderDriver extends AbstractVerticle {

    private final int FRAMES_PER_MACHINE = 50;
    private final int HEARTBEAT_TIMER = 15 * 1000; // 15 seconds
    public static MessageQ MESSAGE_Q;

    private DRenderDriverModel dRenderDriverModel;

    private Logger logger = LoggerFactory.getLogger(DRenderDriver.class);

    public DRenderDriver(){
        dRenderDriverModel = new DRenderDriverModel();
    }

    @Override
    public void start() throws Exception {

        logger.info("Starting...");

        // Deploy all the verticles
        vertx.deployVerticle(new HeartbeatVerticle());
        vertx.deployVerticle(new ResourceManager(), new DeploymentOptions().setMaxWorkerExecuteTime(6* 60L * 1000 * 1000000));
        vertx.deployVerticle(new JobManager());

        // setup listeners for dRender Driver
        EventBus eventBus = vertx.eventBus();

        // Consumer for Project related messages
        eventBus.consumer(Channels.DRIVER_PROJECT)
                .handler(message -> {
                    ProjectRequest projectRequest = Json.decodeValue(message.body().toString(), ProjectRequest.class);
                    DRenderDriver.MESSAGE_Q = new MessageQ(projectRequest.getPublicIP(), "drender.driver.frames");

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

        // Consumer for Instance related messages
        eventBus.consumer(Channels.DRIVER_INSTANCE)
                .handler(message -> {
                    InstanceHeartbeat instance = Json.decodeValue(message.body().toString(), InstanceHeartbeat.class);
                    switch (instance.getAction()) {
                        case START_NEW_MACHINE:
                            //handleNewMachineStart(instance);
                            break;
                        case RESTART_MACHINE:
                        case KILL_MACHINE:
                        default:
                    }
                });

        // Consumer for JobFrame related messages
        eventBus.consumer(Channels.DRIVER_FRAMES)
                .handler(message -> {
                    JsonObject jsonObject = (JsonObject) message.body();
                    JobFrame frame = Json.decodeValue(jsonObject.getString("body"), JobFrame.class);
                    handleFrameRenderedMessage(frame);
                });
    }

    private void setupRabbitMQConsumer() {
        // Setup RabbitMQ consumer
        RabbitMQOptions config = new RabbitMQOptions();
        config.setHost(DRenderDriver.MESSAGE_Q.getHost());
        config.setPort(5672);
        config.setUser("drender");
        config.setPassword("brds");
        config.setVirtualHost("/");

        RabbitMQClient client = RabbitMQClient.create(vertx, config);
        client.start(ar -> {
            if (ar.succeeded()) {
                client.basicConsume(DRenderDriver.MESSAGE_Q.getQueue(), Channels.DRIVER_FRAMES, consumeResult -> {
                    if (consumeResult.succeeded()) {
                        logger.info("RabbitMQ consumer created!");
                    } else {
                        logger.error("Could not create consumer");
                        consumeResult.cause().printStackTrace();
                    }
                });
            } else {
                logger.error("Could not start RabbitMQ client");
            }
        });
    }

    /**
     * Function to drive the entire process of starting a new project and scheduling new tasks.
     * 1. Initialize project parameters
     * 2. Prepare jobMap based on the total number of frames to render
     * 3. Spawn machines based on number of jobs
     * 4. Create output directory in object storage (Example - S3)
     * 5. Update jobMap with the newly retrieved IPs
     * 6. Schedule Heartbeat tasks for each of the machines (Jobs)
     * 7. Start jobMap in each machine
     * 8. Return response with all the current information
     * @param projectRequest
     * @return
     */
    private Future<ProjectResponse> startProject(ProjectRequest projectRequest) {
        logger.info("Received new request: " + Json.encode(projectRequest));

        Project project = initProjectParameters(projectRequest);
        setupRabbitMQConsumer();

        String cloudAMI = ImageFactory.getJobImageAMI(project.getSoftware());

        Future<List<DRenderInstance>> instancesFuture = spawnMachines(cloudAMI, dRenderDriverModel.getAllJobs(project.getID()).size());
        Future<S3Source> outputURIFuture = getOutputSource(project);
        Future<ProjectResponse> projectResponseFuture = Future.future();

        CompositeFuture.all(instancesFuture, outputURIFuture)
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        List<DRenderInstance> instances = instancesFuture.result();
                        S3Source outputURI = outputURIFuture.result();
                        project.setOutputURI(outputURI);
                        // Updates each job with newly retrieved IPs and outputURI
                        List<String> jobIds = dRenderDriverModel.getAllJobIds(project.getID());
                        int instanceIdx = 0;
                        for (String jobID : jobIds) {
                            dRenderDriverModel.updateJobInstance(jobID, instances.get(instanceIdx));
                            dRenderDriverModel.updateJobOutputURI(jobID, outputURI);
                            instanceIdx++;
                        }

                        // Start jobs
                        for (Job job : dRenderDriverModel.getAllJobs(project.getID())) {
                            job.setAction(JobAction.START);
                            startJob(job);
                        }

                        // Schedule heartbeat checks for the newly created jobMap
                        for (DRenderInstance instance : instances) {
                            scheduleHeartbeat(instance);
                        }

                        projectResponseFuture.complete(buildStatus(project));

                    } else {
                        logger.error("Could not create instances or output bucket: " + ar.cause());
                        projectResponseFuture.fail(new Exception());
                    }
                });

        return projectResponseFuture;
    }

    /**
     * Initializes project, generates jobMap and updates the project->job map
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
        List<Job> newJobs = prepareJobs(project);
        dRenderDriverModel.addNewProject(project);
        dRenderDriverModel.addNewJobs(newJobs, project.getID());

        return project;
    }

    /**
     * Constructs log object for this project. This is used to construct
     * current status of the jobMap in the project.
     * @param project
     * @return
     */
    private JsonObject constructLog(Project project) {
        JsonObject log = new JsonObject();
        JsonArray jobLogs = new JsonArray();

        for (Job job : dRenderDriverModel.getAllJobs(project.getID())) {
            jobLogs.add(
                new JsonObject()
                .put("id", job.getID())
                .put("startFrame", job.getStartFrame())
                .put("endFrame", job.getEndFrame())
                .put("instanceInfo", JsonObject.mapFrom(job.getInstance()))
                .put("isActive", job.isActive())
                .put("framesRendered", dRenderDriverModel.getFrameRenderedCount(job.getID()))
            );
        }
        log.put("jobs", jobLogs);
        return log;
    }

    /**
     * Prepares Jobs based on the number of frames to be rendered.
     * Uses static FRAMES_PER_MACHINE to divide frames among Jobs.
     * Sets the job action to START_NEW_MACHINE
     * @param project
     * @return
     */
    private List<Job> prepareJobs(Project project) {
        int currentFrame = project.getStartFrame();
        List<Job> jobs = new ArrayList<>();

        while (currentFrame < project.getEndFrame()) {
            int startFrame = currentFrame;
            int endFrame = (project.getEndFrame() - currentFrame) >= FRAMES_PER_MACHINE ? (currentFrame+FRAMES_PER_MACHINE-1) : project.getEndFrame();

            Job job = Job.builder()
                        .startFrame(startFrame)
                        .endFrame(endFrame)
                        .projectID(project.getID())
                        .source(project.getSource())
                        .action(JobAction.START)
                        .messageQ(DRenderDriver.MESSAGE_Q)
                        .build();

            jobs.add(job);

            currentFrame = endFrame + 1;
        }
        return jobs;
    }

    /**
     * Asynchronously spawns machines. Number of machines spawned is specified in the method.
     * Communicates with ResourceManager through messages in the event queue.
     * Callback returns the instances to caller once complete.
     * @param cloudAMI
     * @param count
     * @return
     */
    private Future<List<DRenderInstance>> spawnMachines(String cloudAMI, int count) {
        EventBus eventBus = vertx.eventBus();
        InstanceRequest instanceRequest = new InstanceRequest(cloudAMI, count);

        final Future<List<DRenderInstance>> ips = Future.future();
        final long TIMEOUT = 6 * 60 * 1000; // 6 minutes (in ms)

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

        eventBus.send(Channels.NEW_STORAGE, project.getID(),
            ar -> {
                if (ar.succeeded()) {
                    future.complete(Json.decodeValue(ar.result().body().toString(), S3Source.class));
                }
            }
        );

        return future;
    }

    private Future<Boolean> checkSource(S3Source source) {
        EventBus eventBus = vertx.eventBus();
        final Future<Boolean> future = Future.future();

        eventBus.send(Channels.CHECK_STORAGE, Json.encode(source),
            ar -> {
                if (ar.succeeded()) {
                    JsonObject jsonObject = (JsonObject) ar.result().body();
                    future.complete(jsonObject.getBoolean("exists"));
                } else {
                    future.fail("Could not verify storage: " + ar.cause());
                }
            }
        );
        return future;
    }

    /**
     * Schedules timed heartbeat checks for the given DRenderInstance.
     * Does not expect a reply, since if the machine runs okay, nothing needs to be done.
     * @param instance
     */
    private void scheduleHeartbeat(DRenderInstance instance) {
        long TIMEOUT = 30 * 1000; // 30 seconds
        long timerId = vertx.setPeriodic(HEARTBEAT_TIMER, id -> {
            EventBus eventBus = vertx.eventBus();
            InstanceHeartbeat instanceHeartbeat = new InstanceHeartbeat(instance, DRenderInstanceAction.HEARTBEAT_CHECK);
            eventBus.send(Channels.HEARTBEAT, Json.encode(instanceHeartbeat), new DeliveryOptions().setSendTimeout(TIMEOUT));
        });

        dRenderDriverModel.updateInstanceTimer(instance.getID(), timerId);
    }

    /**
     * Removes scheduled heartbeat check for this instance
     * @param instance
     */
    private void removeHeartbeat(DRenderInstance instance) {
        long timerId = dRenderDriverModel.getInstanceHeartbeatTimer(instance.getID());
        if (vertx.cancelTimer(timerId)) {
            logger.info("Unregistered timer for instance: " + instance);
        } else {
            logger.info("Could not unregister. Timer does not exist for instance: " + instance);
        }
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
                    job.setActive(true);
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
                        .isComplete(dRenderDriverModel.isProjectComplete(project.getID()))
                        .log(constructLog(project)).build();
    }

    /**
     * Returns current status of the project.
     * Includes logs of the jobMap running currently, and their statuses
     * @param projectID
     * @return
     */
    private ProjectResponse getStatus(String projectID) {
        Project project = dRenderDriverModel.getProject(projectID);
        return project != null ? buildStatus(project) : new ProjectResponse();
    }

    /**
     * Starts a new machine for this job
     * 1. Determines the number of frames to allocate for this job (some frames might already be rendered).
     *    - Might have to split into multiple jobMap if frames are not contiguous (will send it to the same machine).
     * 2. Updates internal data structures to reflect the new jobMap
     * 3. Since the current job (and hence, its instance) is no longer valid, the associated heartbeat
     *    checks are also disabled.
     * @param instanceHeartbeat
     */
    private void handleNewMachineStart(InstanceHeartbeat instanceHeartbeat) {
        List<Job> instanceJobs = dRenderDriverModel.getAllJobs(instanceHeartbeat.getInstance());

        // deactivate old jobs
        instanceJobs.forEach(job -> dRenderDriverModel.updateJobActiveState(job.getID(), false));

        List<Job> newJobs = instanceJobs.stream()
                                .map(this::prepareNewJobs)
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList());

        if (newJobs.size() > 0) {
            String projectID = newJobs.get(0).getProjectID();
            String software = dRenderDriverModel.getProject(projectID).getSoftware();
            spawnMachines(ImageFactory.getJobImageAMI(software), 1)
                    .setHandler(ar -> {
                        if (ar.succeeded()) {
                            DRenderInstance instance = ar.result().get(0);
                            // Assign newly created jobs
                            // Schedule heartbeat
                            newJobs.forEach(job -> job.setInstance(instance));
                            dRenderDriverModel.addNewJobs(newJobs, projectID);

                            scheduleHeartbeat(instance);
                        }
                    });
        }

        // Remove timer for old instance
        removeHeartbeat(instanceHeartbeat.getInstance());
        dRenderDriverModel.removeInstance(instanceHeartbeat.getInstance());
    }

    private List<Job> prepareNewJobs(Job job) {
        int startFrame = job.getStartFrame();
        int endFrame = job.getEndFrame();
        List<Integer> frames = dRenderDriverModel.getRenderedFramesForJob(job.getID())
                                            .stream()
                                            .sorted().collect(Collectors.toList());

        List<Job> newJobs = new ArrayList<>();

        int prevFrame = startFrame-1;
        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i) != prevFrame+1) {
                newJobs.add(
                  Job.builder()
                  .startFrame(prevFrame+1)
                  .endFrame(frames.get(i)-1)
                  .source(job.getSource())
                  .action(JobAction.START)
                  .messageQ(job.getMessageQ())
                  .projectID(job.getProjectID())
                  .outputURI(job.getOutputURI()).build()
                );
            }
            prevFrame = frames.get(i);
        }

        if (prevFrame != endFrame) {
            newJobs.add(Job.builder()
                    .startFrame(prevFrame+1)
                    .endFrame(endFrame)
                    .source(job.getSource())
                    .action(JobAction.START)
                    .messageQ(job.getMessageQ())
                    .projectID(job.getProjectID())
                    .outputURI(job.getOutputURI()).build());
        }

        return newJobs;
    }

    private void handleFrameRenderedMessage(JobFrame jobFrame) {
        checkSource(jobFrame.getOutputURI())
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        boolean exists = ar.result();
                        if (exists) {
                            dRenderDriverModel.updateJobFrames(jobFrame.getJobId(), jobFrame.getLastFrameRendered());
                        } else {
                            logger.error("Frame does not exist: " + jobFrame);
                        }
                    } else {
                        logger.error("Could not handle frame render message: " + jobFrame);
                    }
                });
    }
}
