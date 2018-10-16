package com.drender.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class Job {
    private String ID;
    private String machineIP;
    private int startFrame;
    private int endFrame;
    private JobAction action;
    private String outputURI;

    @Builder
    public Job(String machineIP, int startFrame, int endFrame, JobAction action, String outputURI) {
        this.ID = UUID.randomUUID().toString();
        this.machineIP = machineIP;
        this.startFrame = endFrame;
        this.action = action;
        this.outputURI = outputURI;
    }
}
