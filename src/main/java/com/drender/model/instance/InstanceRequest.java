package com.drender.model.instance;

import com.drender.model.project.JsonObjectDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vertx.core.json.JsonObject;
import lombok.*;

import java.util.List;

/**
 * Machine request object to request one or more machines
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class InstanceRequest {
    private DRenderInstanceAction action;

    @JsonDeserialize(using = JsonObjectDeserializer.class)
    private JsonObject request;
}
