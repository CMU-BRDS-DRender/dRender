package com.drender;

import com.drender.model.Channels;
import com.drender.model.project.ProjectAction;
import com.drender.model.project.ProjectRequest;
import com.drender.model.project.ProjectResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MasterController extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {
        // deploy Driver
        vertx.deployVerticle(new DRenderDriver());

        Router router = Router.router(vertx);

        // Allow reading of request body
        router.route().handler(BodyHandler.create());

        // start defining routes
        // Start new ProjectRequest
        router.post("/start/")
            .handler(this::startProject);

        // Status of project
        router.get("/status")
            .handler(this::status);

        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        // Retrieve port from configuration
                        // default to 8080
                        config().getInteger("http.port", 8080),
                        result -> {
                            if (result.succeeded()){
                                future.complete();
                            } else {
                                future.fail(result.cause());
                            }
                        }
                );
    }

    private void startProject(RoutingContext routingContext) {
        ProjectRequest projectRequest = Json.decodeValue(routingContext.getBodyAsString(), ProjectRequest.class);

        System.out.println("MasterController: Received new request: ");
        System.out.println(Json.encode(projectRequest));

        // Send the start message to Driver
        EventBus eventBus = vertx.eventBus();
        eventBus.send(Channels.DRIVER_PROJECT, Json.encode(projectRequest),
            ar -> {
                if (ar.succeeded()) {
                    ProjectResponse response = Json.decodeValue(ar.result().body().toString(), ProjectResponse.class);
                    routingContext.response()
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encode(response));
                }
            }
        );
    }

    private void status(RoutingContext routingContext) {
        ProjectRequest projectRequest = ProjectRequest.builder()
                                                    .action(ProjectAction.STATUS)
                                                    .build();

        // Send getStatus message to Driver
        EventBus eventBus = vertx.eventBus();
        eventBus.send(Channels.DRIVER_PROJECT, Json.encode(projectRequest),
            ar -> {
                if (ar.succeeded()) {
                    routingContext.response()
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encode(ar.result().body()));
                }
            }
        );
    }
}
