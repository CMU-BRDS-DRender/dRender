package com.drender.eventprocessors;

import com.drender.cloud.AWSProvider;
import com.drender.cloud.CloudProvider;
import com.drender.model.Channels;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.Instance;
import com.drender.model.instance.InstanceResponse;
import com.drender.model.job.Job;
import com.drender.model.instance.InstanceRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;

import java.util.Collections;

public class InstanceProvisioner extends AbstractVerticle {

    /*
        Currently setup for AWS
     */
    private CloudProvider<AWSRequestProperty> cloudProvider = new AWSProvider();

    @Override
    public void start() throws Exception {
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.PROVISIONER)
                .handler(message -> {
                    InstanceRequest instanceRequest = Json.decodeValue(message.body().toString(), InstanceRequest.class);

                    System.out.println("InstanceProvisioner: Received new request: ");
                    System.out.println(Json.encode(instanceRequest));

                    InstanceResponse response = new InstanceResponse("success",
                            Collections.singletonList(startNewInstance(instanceRequest.getJobs().get(0))));
                    message.reply(Json.encode(response));
                });
    }

    private Instance startNewInstance(Job job) {
        String name = job.getProjectID() + "_" + job.getID() + "_" + job.getStartFrame() + "_" + job.getEndFrame();
        AWSRequestProperty awsRequestProperty = new AWSRequestProperty(name);

        return cloudProvider.startMachine(awsRequestProperty);
    }
}
