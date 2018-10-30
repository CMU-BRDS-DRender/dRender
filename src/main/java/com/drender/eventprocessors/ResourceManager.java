package com.drender.eventprocessors;

import com.drender.cloud.StorageProvider;
import com.drender.cloud.aws.AWSProvider;
import com.drender.cloud.MachineProvider;
import com.drender.cloud.aws.S3BucketProvisioner;
import com.drender.model.Channels;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.S3Source;
import com.drender.model.instance.DRenderInstance;
import com.drender.model.instance.InstanceResponse;
import com.drender.model.job.Job;
import com.drender.model.instance.InstanceRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;

import java.util.List;
import java.util.stream.Collectors;

public class ResourceManager extends AbstractVerticle {

    /*
        Currently setup for AWS
     */
    private MachineProvider<AWSRequestProperty> machineProvider = new AWSProvider();
    private StorageProvider storageProvider = new S3BucketProvisioner();

    @Override
    public void start() throws Exception {
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.INSTANCE_MANAGER)
                .handler(message -> {
                    InstanceRequest instanceRequest = Json.decodeValue(message.body().toString(), InstanceRequest.class);

                    System.out.println("InstanceManager: Received new request");
                    System.out.println(Json.encode(instanceRequest));

                    InstanceResponse response = new InstanceResponse("success", getNewInstances(instanceRequest.getJobs()));
                    message.reply(Json.encode(response));
                });

        eventBus.consumer(Channels.STORAGE_MANAGER)
                .handler(message -> {
                    String projectID = message.body().toString();

                    System.out.println("StorageManager: Received new request: " + projectID);

                    S3Source response = storageProvider.createStorage(projectID);
                    message.reply(Json.encode(response));
                });
    }

    private List<DRenderInstance> getNewInstances(List<Job> jobs) {
        String region = "us-east-1";
        String imageId = "<image-id>";
        String securityGroupName = "";
        String sshKeyName = "";
        List<String> nameList = jobs.stream().map(Job::getMachineName).collect(Collectors.toList());
        AWSRequestProperty awsRequestProperty = new AWSRequestProperty(sshKeyName, securityGroupName,nameList, region, imageId);
        return machineProvider.startMachines(awsRequestProperty);
    }
}
