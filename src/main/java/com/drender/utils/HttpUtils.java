package com.drender.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

public class HttpUtils {

    private WebClient client;
    private Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private final long GET_TIMEOUT = 10 * 1000; // 10 seconds (in ms)

    public HttpUtils(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    public <R> Future<R> get(String domain, String uri, int port, Class<R> clazz, int retryCount) {
        final Future<R> future = Future.future();

        logger.info("GET Request: " + domain + ":" + port + uri);

        client
            .get(port, domain, uri)
            .timeout(GET_TIMEOUT) // 10 seconds
            .putHeader("content-type", "application/json")
            .send(ar -> {
                if (ar.succeeded()) {
                    HttpResponse<Buffer> response = ar.result();
                    R responseObject = Json.decodeValue(response.body(), clazz);
                    logger.info("Received response: " + response.bodyAsString());

                    future.complete(responseObject);
                } else {
                    logger.error("GET Failed: " + ar.cause());

                    if (retryCount - 1 > 0) {
                        get(domain, uri, port, clazz, retryCount-1)
                            .setHandler(ar1 -> {
                                if (ar1.succeeded()) {
                                    future.complete(ar1.result());
                                } else {
                                    future.fail(ar1.cause());
                                }
                            });
                    } else {
                        future.fail(ar.cause());
                    }
                }
            });

        return future;
    }

    public <R> Future<R> get(String domain, String uri, int port, Class<R> clazz) {
        final Future<R> future = Future.future();

        get(domain, uri, port, clazz, 1)
            .setHandler(ar -> {
                if (ar.succeeded()) {
                    future.complete(ar.result());
                } else {
                    future.fail(ar.cause());
                }
            });

        return future;
    }

    public Future<Void> get(String domain, String uri, int port) {
        final Future<Void> future = Future.future();

        logger.info("GET Request: " + domain + ":" + port + uri);

        client
                .get(port, domain, uri)
                .timeout(GET_TIMEOUT) // 10 seconds
                .putHeader("content-type", "application/json")
                .as(BodyCodec.none())
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Void> response = ar.result();
                        logger.info("Received response: " + response.bodyAsString());

                        future.complete();
                    } else {
                        logger.error("GET Failed: " + ar.cause());
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
                    ar.cause().printStackTrace();
                    future.fail("POST Failed: " + ar.cause());
                }
            });

        return future;
    }
}
