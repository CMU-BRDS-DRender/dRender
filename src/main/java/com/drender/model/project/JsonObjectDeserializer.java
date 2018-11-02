package com.drender.model.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.Map;

/**
 * Need to use custom deserializer for JsonObject, as it does not implement the deserializer
 * https://github.com/eclipse-vertx/vert.x/issues/2247
 */
public class JsonObjectDeserializer extends StdDeserializer<JsonObject> {

    private final TypeReference<Map<String, Object>> type = new TypeReference<Map<String, Object>>() {};

    public JsonObjectDeserializer() {
        this(null);
    }

    public JsonObjectDeserializer(final Class<?> type) {
        super(type);
    }

    @Override
    public JsonObject deserialize(final JsonParser jsonParser, final DeserializationContext context) throws IOException {
        final ObjectCodec objectCodec = jsonParser.getCodec();
        final Map<String, Object> value = objectCodec.readValue(jsonParser, type);

        return new JsonObject(value);
    }

}