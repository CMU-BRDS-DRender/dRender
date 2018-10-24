package com.drender.model.project;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private String source;
    private int startFrame;
    private int endFrame;
    private String outputURI;
    private JsonObject log;
}
