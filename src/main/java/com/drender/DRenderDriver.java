package com.drender;

import com.drender.cloud.ImageFactory;
import com.drender.eventprocessors.DRenderLogger;
import com.drender.eventprocessors.HeartbeatVerticle;
import com.drender.eventprocessors.MachineProvisioner;
import com.drender.model.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DRenderDriver extends AbstractVerticle {

    private final int FRAMES_PER_MACHINE = 20;

    private Project project;
    private Map<String, Job> jobByID;

    public DRenderDriver(){
        project = new Project();
        jobByID = new HashMap<>();
    }

    @Override
    public void start() throws Exception {

        // Deploy all the verticles
        vertx.deployVerticle(new DRenderLogger());
        vertx.deployVerticle(new HeartbeatVerticle());
        vertx.deployVerticle(new MachineProvisioner());

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

    private ProjectResponse startProject(ProjectRequest projectRequest) {
        initProjectParameters(projectRequest);

        String cloudAMI = ImageFactory.getImageAMI(project.getSoftware());
        prepareJobs();
        spawnMachines(cloudAMI);
        return new ProjectResponse();
    }

    private void initProjectParameters(ProjectRequest projectRequest) {
        project.setID(projectRequest.getId());
        project.setSource(projectRequest.getSource());
        project.setSoftware(projectRequest.getSoftware());
        project.setStartFrame(projectRequest.getStartFrame());
        project.setEndFrame(projectRequest.getEndFrame());
    }

    private void prepareJobs() {
        int currentFrame = project.getStartFrame();

        while (currentFrame < project.getEndFrame()) {
            int startFrame = currentFrame;
            int endFrame = (project.getEndFrame() - currentFrame) >= FRAMES_PER_MACHINE ? (currentFrame+FRAMES_PER_MACHINE) : project.getEndFrame();

            Job job = Job.builder()
                        .startFrame(startFrame)
                        .endFrame(endFrame)
                        .action(JobAction.START_NEW_MACHINE)
                        .build();
            jobByID.put(job.getID(), job);

            currentFrame += FRAMES_PER_MACHINE;
        }
    }

    private List<String> spawnMachines(String cloudAMI) {
        return new ArrayList<>();
    }

    private ProjectResponse getStatus() {
        return ProjectResponse.builder()
                .log(new JsonObject("{\"message\" : \"All good\" }"))
                .build();
    }
}
