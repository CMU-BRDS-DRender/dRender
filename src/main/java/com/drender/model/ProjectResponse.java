package com.drender.model;

import io.vertx.core.json.JsonObject;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {
    private int id;
    private String software;
    private String source;
    private int startFrame;
    private int endFrame;
    private String outputURI;
    private JsonObject log;
}
