package com.drender.model.job;

import com.drender.model.cloud.S3Source;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobFrame {
    private String jobID;
    private int lastFrameRendered;
    private S3Source outputURI;
}
