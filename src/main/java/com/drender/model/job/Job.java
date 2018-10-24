package com.drender.model.job;

import com.drender.model.cloud.Instance;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Job {
    private String ID;
    private String projectID;
    private Instance instance;
    private int startFrame;
    private int endFrame;
    private JobAction action;
    private String outputURI;

    @Builder
    public Job(Instance instance, String projectID, int startFrame, int endFrame, JobAction action, String outputURI) {
        this.ID = UUID.randomUUID().toString();
        this.projectID = projectID;
        this.instance = instance;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.action = action;
        this.outputURI = outputURI;
    }
}
