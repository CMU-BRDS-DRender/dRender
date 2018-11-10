package com.drender.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class HttpUtils {

    private WebClient client;
    private Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public HttpUtils(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    public <R> Future<R> get(String domain, String uri, int port, Class<R> clazz) {
        final Future<R> future = Future.future();

        logger.info("GET Request: " + domain + ":" + port + uri);

        client
            .get(port, domain, uri)
            .timeout(5 * 1000) // 5 seconds
            .putHeader("content-type", "application/json")
            .send(ar -> {
                if (ar.succeeded()) {
                    HttpResponse<Buffer> response = ar.result();
                    R responseObject = Json.decodeValue(response.body(), clazz);
                    logger.info("Received response: " + response.bodyAsString());

                    future.complete(responseObject);
                } else {
                    future.fail("GET Failed: " + ar.cause());
                }
            });
        return future;
    }

    public <T, R> Future<R> post(String domain, String uri, int port, T requestBody, Class<R> resClass) {
        final Future<R> future = Future.future();

        logger.info("POST Request: " + domain + ":" + port + uri + " Body: " + Json.encode(requestBody));

        client
            .post(port, domain, uri)
            .putHeader("content-type", "application/json")
            .sendJson(requestBody, ar -> {
                if (ar.succeeded()) {
                    HttpResponse<Buffer> response = ar.result();
                    R responseObject = Json.decodeValue(response.body(), resClass);
                    logger.info("Received response: " + response.bodyAsString());
                    future.complete(responseObject);
                } else {
                    future.fail("POST Failed: " + ar.cause());
                }
            });
        return future;
    }
}
