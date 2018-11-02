package com.drender.model.project;

import com.drender.model.cloud.S3Source;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vertx.core.json.JsonObject;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectResponse {
    private String id;
    private String software;
    private S3Source source;
    private int startFrame;
    private int endFrame;
    private S3Source outputURI;
    private boolean isComplete;

    @JsonDeserialize(using = JsonObjectDeserializer.class)
    private JsonObject log;
}
