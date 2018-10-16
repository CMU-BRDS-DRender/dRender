package com.drender.eventprocessors;

import com.drender.model.Channels;
import com.drender.model.Job;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;

public class MachineProvisioner extends AbstractVerticle {

    /*
        Currently setup for
     */

    @Override
    public void start() throws Exception {
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.PROVISIONER)
                .handler(message -> {
                    Job job = Json.decodeValue(message.body().toString(), Job.class);
                    switch (job.getAction()) {
                        case START_NEW_MACHINE:
                            String ip = startNewMachine(job);
                    }
                });
    }

    private String startNewMachine(Job job) {
        return "<dummy-ip>";
    }
}
