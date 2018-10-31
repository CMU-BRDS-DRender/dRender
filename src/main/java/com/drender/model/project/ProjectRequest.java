package com.drender.model.project;

import com.drender.model.cloud.S3Source;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectRequest {
    private String id;
    private String software;
    private S3Source source;
    private int startFrame;
    private int endFrame;

    private ProjectAction action;
}
