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
import java.net.InetSocketAddress;
import java.net.Socket;

public class HeartbeatVerticle extends AbstractVerticle {

    private HttpUtils httpUtils;
    private Logger logger = LoggerFactory.getLogger(HeartbeatVerticle.class);
    private final int TIMEOUT = 10000; // 10 secs

    @Override
    public void start() throws Exception {
        logger.info("Starting...");

        httpUtils = new HttpUtils(vertx);

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.HEARTBEAT)
                .handler(message -> {
                    InstanceHeartbeat instanceHeartbeat = Json.decodeValue(message.body().toString(), InstanceHeartbeat.class);

                    httpUtils.get(instanceHeartbeat.getInstance().getIp(), "/nodeStatus", 8080, JobResponse.class)
                            .setHandler(ar -> {
                                if (!ar.succeeded()) {
                                    logger.info("Heartbeat failed for: " + instanceHeartbeat.getInstance());
                                    DRenderInstanceAction nextAction = DRenderInstanceAction.START_NEW_MACHINE;

                                    // status check failed. Try pinging the machine
                                    if (isReachable(instanceHeartbeat.getInstance().getIp())) {
                                        logger.info("Machine " + instanceHeartbeat.getInstance().getIp() + " reachable");
                                        nextAction = DRenderInstanceAction.RESTART_MACHINE;
                                    } else {
                                        logger.info("Could not reach machine for instance: " + message.body().toString());
                                    }

                                    // send appropriate message for job to DRenderDriver
                                    logger.info("Sending action " + nextAction + " for Instance: " + instanceHeartbeat.getInstance());
                                    instanceHeartbeat.setAction(nextAction);
                                    eventBus.send(Channels.DRIVER_INSTANCE, Json.encode(instanceHeartbeat));
                                }
                            });
                });
    }

    private boolean isReachable(String address) {
        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(address, 22), TIMEOUT);
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
