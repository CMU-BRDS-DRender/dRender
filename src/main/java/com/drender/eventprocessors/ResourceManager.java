package com.drender.eventprocessors;

import com.drender.cloud.StorageProvider;
import com.drender.cloud.aws.AWSProvider;
import com.drender.cloud.MachineProvider;
import com.drender.cloud.aws.S3BucketManager;
import com.drender.model.Channels;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.S3Source;
import com.drender.model.instance.DRenderInstance;
import com.drender.model.instance.InstanceResponse;
import com.drender.model.instance.InstanceRequest;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class ResourceManager extends AbstractVerticle {

    /*
        Currently setup for AWS
     */
    private MachineProvider<AWSRequestProperty> machineProvider = new AWSProvider();
    private StorageProvider storageProvider = new S3BucketManager();

    private final String SUFFIX = "/";
    private final String OUTPUT_FOLDER = "output";

    private Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    @Override
    public void start() throws Exception {
        logger.info("Starting...");

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.INSTANCE_MANAGER)
                .handler(message -> {
                    InstanceRequest instanceRequest = Json.decodeValue(message.body().toString(), InstanceRequest.class);

                    logger.info("Received new instance request: " + message.body().toString());

                    List<DRenderInstance> instances = getNewInstances(instanceRequest.getCloudAMI(), instanceRequest.getCount());

                    //future.complete(instances);

                    // Need to reply here rather than the callback since the result of this event becomes false (not succeeded)
                    InstanceResponse response = new InstanceResponse("success", instances);
                    message.reply(Json.encode(response));
                });

        eventBus.consumer(Channels.NEW_STORAGE)
                .handler(message -> {
                    String projectID = message.body().toString();

                    logger.info("Received new storage request for project with ID: " +  projectID);

                    String folderName = projectID + SUFFIX + OUTPUT_FOLDER;
                    S3Source response = storageProvider.createStorage(folderName);
                    message.reply(Json.encode(response));
                });

        eventBus.consumer(Channels.CHECK_STORAGE)
                .handler(message -> {
                    S3Source source = Json.decodeValue(message.body().toString(), S3Source.class);
                    boolean exists = storageProvider.checkExists(source);
                    message.reply(exists);
                });
    }

    private List<DRenderInstance> getNewInstances(String cloudAMI, int count) {
        String region = "us-east-1a";
        String securityGroupName = "HTTP Open";
        String sshKeyName = "drender";
        AWSRequestProperty awsRequestProperty = new AWSRequestProperty(sshKeyName, securityGroupName, count, region, cloudAMI);
        return machineProvider.startMachines(awsRequestProperty);
    }
}
