package com.drender.cloud.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
public class HttpUtils {

    private WebClient client;

    public HttpUtils(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    public <R> Future<R> get(String domain, String uri, int port, Class<R> clazz) {
        final Future<R> future = Future.future();

        client
            .get(port, domain, uri)
            .putHeader("content-type", "application/json")
            .send(ar -> {
                if (ar.succeeded()) {
                    HttpResponse<Buffer> response = ar.result();
                    R responseObject = Json.decodeValue(response.body(), clazz);

                    future.complete(responseObject);
                } else {
                    future.fail("Could not convert response: " + ar.cause());
                }
            });
        return future;
    }

    public <T, R> Future<R> post(String domain, String uri, int port, T requestBody, Class<R> resClass) {
        final Future<R> future = Future.future();

        client
            .post(port, domain, uri)
            .putHeader("content-type", "application/json")
            .sendJson(requestBody, ar -> {
                if (ar.succeeded()) {
                    HttpResponse<Buffer> response = ar.result();
                    R responseObject = Json.decodeValue(response.body(), resClass);

                    future.complete(responseObject);
                } else {
                    future.fail("Could not convert response: " + ar.cause());
                }
            });
        return future;
    }
}
