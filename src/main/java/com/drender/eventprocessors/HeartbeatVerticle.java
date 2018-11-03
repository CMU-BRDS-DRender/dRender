package com.drender.eventprocessors;

import com.drender.model.instance.DRenderInstanceAction;
import com.drender.model.instance.InstanceHeartbeat;
import com.drender.utils.HttpUtils;
import com.drender.model.Channels;
import com.drender.model.job.JobResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;

public class HeartbeatVerticle extends AbstractVerticle {

    private HttpUtils httpUtils;
    private Logger logger = LoggerFactory.getLogger(HeartbeatVerticle.class);
    private final int TIMEOUT = 5000; // 5 secs

    @Override
    public void start() throws Exception {
        logger.info("Starting...");

        httpUtils = new HttpUtils(vertx);

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.HEARTBEAT)
                .handler(message -> {
                    InstanceHeartbeat instanceHeartbeat = Json.decodeValue(message.body().toString(), InstanceHeartbeat.class);

                    httpUtils.get(instanceHeartbeat.getInstance().getIp(), "/statusCheck", 8080, JobResponse.class)
                            .setHandler(ar -> {
                                if (!ar.succeeded()) {
                                    DRenderInstanceAction nextAction = DRenderInstanceAction.START_NEW_MACHINE;
                                    // status check failed. Try pinging the machine
                                    try {
                                        InetAddress address = InetAddress.getByName(instanceHeartbeat.getInstance().getIp());
                                        boolean reachable = address.isReachable(TIMEOUT);
                                        if (reachable) nextAction = DRenderInstanceAction.RESTART_MACHINE;
                                    } catch (IOException e) {
                                        logger.info("Could not reach machine for instance: " + message.body().toString());
                                    } finally {
                                        // send appropriate message for job to DRenderDriver
                                        logger.info("Sending action " + nextAction + " for Instance: " + instanceHeartbeat.getInstance());
                                        instanceHeartbeat.setAction(nextAction);
                                        eventBus.send(Channels.DRIVER_INSTANCE, Json.encode(instanceHeartbeat));
                                    }
                                }
                            });
                });
    }
}
