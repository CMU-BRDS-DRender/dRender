package com.drender;

import com.drender.cloud.ImageFactory;
import com.drender.eventprocessors.DRenderLogger;
import com.drender.eventprocessors.HeartbeatVerticle;
import com.drender.eventprocessors.InstanceProvisioner;
import com.drender.model.*;
import com.drender.model.cloud.Instance;
import com.drender.model.job.Job;
import com.drender.model.job.JobAction;
import com.drender.model.instance.InstanceRequest;
import com.drender.model.instance.InstanceResponse;
import com.drender.model.project.Project;
import com.drender.model.project.ProjectRequest;
import com.drender.model.project.ProjectResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class DRenderDriver extends AbstractVerticle {

    private final int FRAMES_PER_MACHINE = 20;

    private Map<Project, Map<String, Job>> projectJobs;

    public DRenderDriver(){
        projectJobs = new HashMap<>();
    }

    @Override
    public void start() throws Exception {

        // Deploy all the verticles
        vertx.deployVerticle(new DRenderLogger());
        vertx.deployVerticle(new HeartbeatVerticle());
        vertx.deployVerticle(new InstanceProvisioner());

        // setup listeners for dRender Driver
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.DRIVER_PROJECT)
                .handler(message -> {
                    ProjectRequest projectRequest = Json.decodeValue(message.body().toString(), ProjectRequest.class);
                    ProjectResponse projectResponse;
                    switch (projectRequest.getAction()) {
                        case START:
                            projectResponse = startProject(projectRequest);
                            break;
                        case STATUS:
                        default:
                            projectResponse = getStatus();
                    }

                    message.reply(Json.encode(projectResponse));
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
    private ProjectResponse startProject(ProjectRequest projectRequest) {
        System.out.println("DRenderDriver: Received new request: ");
        System.out.println(Json.encodePrettily(projectRequest));

        Project project = initProjectParameters(projectRequest);

        String cloudAMI = ImageFactory.getImageAMI(project.getSoftware());
        prepareJobs(project);

        List<Job> jobList = new ArrayList<>(projectJobs.get(project).values());
        List<Instance> instances = spawnMachines(cloudAMI, jobList);

        return new ProjectResponse();
    }

    private Project initProjectParameters(ProjectRequest projectRequest) {
        Project project = Project.builder()
                            .ID(projectRequest.getId())
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
                        .action(JobAction.START_NEW_MACHINE)
                        .build();

            jobMap.put(job.getID(), job);

            currentFrame += FRAMES_PER_MACHINE;
        }

        return jobMap;
    }

    private List<Instance> spawnMachines(String cloudAMI, List<Job> jobs) {
        EventBus eventBus = vertx.eventBus();
        InstanceRequest instanceRequest = new InstanceRequest(cloudAMI, jobs);

        List<Instance> ips = new ArrayList<>();

        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("Content-Length", "");
        eventBus.send(Channels.PROVISIONER, Json.encode(instanceRequest),
            ar -> {
                if (ar.succeeded()) {
                    InstanceResponse response = Json.decodeValue(ar.result().body().toString(), InstanceResponse.class);
                    ips.addAll(response.getInstances());
                }
            }
        );

        return ips;
    }

    private ProjectResponse getStatus() {
        return ProjectResponse.builder()
                .log(new JsonObject("{\"message\" : \"All good\" }"))
                .build();
    }
}
