package com.drender.model.job;

import com.drender.model.cloud.S3Source;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobResponse {
    private String ID;
    private String projectID;
    private S3Source outputURI;
    private String message;
}
