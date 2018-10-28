package com.drender.model.job;

import com.drender.model.cloud.S3Source;
import com.drender.model.cloud.DrenderInstance;
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
    private String machineName;
    private String projectID;
    private S3Source source;
    private DrenderInstance instance;
    private int startFrame;
    private int endFrame;
    private JobAction action;
    private String outputURI;

    @Builder
    public Job(DrenderInstance instance, String projectID, S3Source source, int startFrame, int endFrame, JobAction action, String outputURI) {
        this.ID = UUID.randomUUID().toString();
        this.projectID = projectID;
        this.source = source;
        this.instance = instance;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.action = action;
        this.outputURI = outputURI;
        this.machineName = projectID + "_" + ID + "_" + startFrame + "_" + endFrame;
    }
}
