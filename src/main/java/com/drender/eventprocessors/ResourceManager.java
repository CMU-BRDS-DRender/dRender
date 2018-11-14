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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

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
                        JsonObject request = instanceRequest.getRequest();
                        switch (instanceRequest.getAction()) {
                            case START_NEW_MACHINE:
                                vertx.executeBlocking(future -> {
                                    logger.info("Received new instance request: " + message.body().toString());

                                    List<DRenderInstance> instances = getNewInstances(request.getString("cloudAMI"),
                                                                                      request.getInteger("count"));

                                    future.complete(instances);

                                    // Need to reply here rather than the callback since the result of this event becomes false (not succeeded)
                                    InstanceResponse response = new InstanceResponse("success", instances);
                                    message.reply(Json.encode(response));
                                }, result -> {
                                    // Do nothing
                                });
                                break;

                            case RESTART_MACHINE:
                            case KILL_MACHINE:
                                JsonArray instances = request.getJsonArray("instances");
                                List<String> instanceIds = instances
                                                                .stream()
                                                                .map(Object::toString)
                                                                .collect(Collectors.toList());
                                vertx.executeBlocking(future -> {
                                    logger.info("Received instance termination request: " + instanceIds);
                                    killInstances(instanceIds);
                                    future.complete();

                                    InstanceResponse response = InstanceResponse.builder().message("success").build();
                                    message.reply(Json.encode(response));
                                }, result -> {

                                });
                        }
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
                    logger.info("Received frame info: " + message.body().toString());
                    S3Source source = Json.decodeValue(message.body().toString(), S3Source.class);
                    boolean exists = storageProvider.checkExists(source);
                    JsonObject existsResponse = new JsonObject().put("exists", exists);
                    message.reply(existsResponse);
                });
    }

    private List<DRenderInstance> getNewInstances(String cloudAMI, int count) {
        String region = "us-east-1a";
        String securityGroupName = "HTTP Open";
        String sshKeyName = "drender";
        AWSRequestProperty awsRequestProperty = new AWSRequestProperty(sshKeyName, securityGroupName, region, cloudAMI);
        return machineProvider.startMachines(awsRequestProperty, count);
    }

    private void killInstances(List<String> instanceIds) {
        String region = "us-east-1a";
        String securityGroupName = "HTTP Open";
        String sshKeyName = "drender";
        AWSRequestProperty awsRequestProperty = new AWSRequestProperty(sshKeyName, securityGroupName, region);

        machineProvider.killMachines(awsRequestProperty, instanceIds);
    }
}
