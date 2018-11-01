package com.drender;

import com.drender.model.Channels;
import com.drender.model.cloud.S3Source;
import com.drender.model.project.ProjectAction;
import com.drender.model.project.ProjectRequest;
import com.drender.model.project.ProjectResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MasterController extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(MasterController.class);

    @Override
    public void start(Future<Void> future) {
        logger.info("Starting...");

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
        router.get("/status/:projectID")
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

    /**
     * /start/
     * @param routingContext
     */
    private void startProject(RoutingContext routingContext) {
        ProjectRequest projectRequest = Json.decodeValue(routingContext.getBodyAsString(), ProjectRequest.class);

        logger.info("Received new project request: " + Json.encode(projectRequest));

        final long TIMEOUT = 5 * 60 * 1000; // 5 minutes (in ms)

        // Send the start message to Driver
        EventBus eventBus = vertx.eventBus();
        eventBus.send(Channels.DRIVER_PROJECT, Json.encode(projectRequest), new DeliveryOptions().setSendTimeout(TIMEOUT),
            ar -> {
                if (ar.succeeded()) {
                    ProjectResponse response = Json.decodeValue(ar.result().body().toString(), ProjectResponse.class);
                    routingContext.response()
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encode(response));
                } else {
                    logger.error("Failed to start project: " + ar.cause());
                }
            }
        );
    }

    /**
     * /status/:projectID
     * @param routingContext
     */
    private void status(RoutingContext routingContext) {
        ProjectRequest projectRequest = ProjectRequest.builder()
                                                    .id(routingContext.request().getParam("projectID"))
                                                    .action(ProjectAction.STATUS)
                                                    .build();

        logger.info("Received status request: " + Json.encode(projectRequest));

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
