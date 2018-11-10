package com.drender.cloud.aws;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterHandler;
import com.amazonaws.waiters.WaiterParameters;
import com.drender.model.instance.DRenderInstance;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class EC2Provisioner {

    private static AmazonEC2 ec2Client = null;
    private Logger logger = LoggerFactory.getLogger(EC2Provisioner.class);

    private final String S3_FULL_ACCESS_ARN = "arn:aws:iam::214187139358:instance-profile/S3FullAccess";

    public EC2Provisioner(String region, AWSCredentialsProvider credentialProvider){
        if(ec2Client == null) {
            ec2Client = AmazonEC2ClientBuilder.standard()
                        .withCredentials(credentialProvider)
                        .withRegion(region)
                        .build();
        }
    }

    public AmazonEC2 getClient() throws Exception {
        return ec2Client;
    }

    public List<DRenderInstance> spawnInstances(int count, String securityGroup, String sshKeyName, String imageID) throws ExecutionException, InterruptedException {

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(imageID)
                .withInstanceType(InstanceType.T2Micro)
                .withMinCount(count)
                .withMaxCount(count)
                .withKeyName(sshKeyName)
                .withSecurityGroups(securityGroup)
                .withIamInstanceProfile(
                        new IamInstanceProfileSpecification().withArn(S3_FULL_ACCESS_ARN)
                );

        RunInstancesResult result = ec2Client.runInstances(runInstancesRequest);

        List<Instance> instanceList = result.getReservation().getInstances();
        String[] instanceIds = new String[count];
        instanceIds = instanceList.stream().map(Instance::getInstanceId).collect(Collectors.toList()).toArray(instanceIds);

        DescribeInstancesRequest waitRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds);

        ec2Client.waiters().instanceRunning().run(new WaiterParameters<DescribeInstancesRequest>().withRequest(waitRequest));

        instanceList = ec2Client.describeInstances(new DescribeInstancesRequest()
                .withInstanceIds(instanceIds))
                .getReservations()
                .stream()
                .map(Reservation::getInstances)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<DRenderInstance> DRenderInstanceList = new ArrayList<>();
        for( int i = 0 ; i < instanceList.size() ; i++){
            Instance instance = instanceList.get(i);
            //String name = nameList.get(i);
            //Tag nameTag = new Tag("Name", name);
            //List<Tag> tagList = new ArrayList<>();
            //tagList.add(nameTag);
            //instance.setTags(tagList);
            DRenderInstanceList.add(new DRenderInstance(instance.getInstanceId(),instance.getPublicIpAddress()));
        }

        logger.info("Spawned EC2 instances: " + DRenderInstanceList);

        return DRenderInstanceList;
    }

}
