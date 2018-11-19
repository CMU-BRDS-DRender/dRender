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
import com.drender.model.instance.VerifyRequest;
import com.drender.model.job.JobResponse;
import com.drender.utils.HttpUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class EC2Provisioner {

    private static AmazonEC2 ec2Client = null;
    private Logger logger = LoggerFactory.getLogger(EC2Provisioner.class);
    private final long VERIFY_TIMEOUT = 5 * 60 * 1000; // 5 mins

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
        DescribeInstanceStatusRequest statusRequest = new DescribeInstanceStatusRequest().withInstanceIds(instanceIds);

        ec2Client.waiters().instanceRunning().run(new WaiterParameters<DescribeInstancesRequest>().withRequest(waitRequest));
        ec2Client.waiters().instanceStatusOk().run(new WaiterParameters<DescribeInstanceStatusRequest>().withRequest(statusRequest));

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

    public void killInstances(List<String> instanceIds) {
        TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();

        terminateInstancesRequest.withInstanceIds(instanceIds);
        TerminateInstancesResult result = ec2Client.terminateInstances(terminateInstancesRequest);

        String[] terminatingList = new String[result.getTerminatingInstances().size()];
        result.getTerminatingInstances()
                .stream()
                .map(InstanceStateChange::getInstanceId)
                .collect(Collectors.toList()).toArray(terminatingList);

        DescribeInstancesRequest waitRequest = new DescribeInstancesRequest().withInstanceIds(terminatingList);

        ec2Client.waiters().instanceTerminated().run(new WaiterParameters<DescribeInstancesRequest>().withRequest(waitRequest));

        logger.info("Killed EC2 instances: " + instanceIds);
    }

    public Future<Void> restartInstances(List<String> instanceIds, VerifyRequest verifyRequest) {
        RebootInstancesRequest rebootInstancesRequest = new RebootInstancesRequest();

        rebootInstancesRequest.withInstanceIds(instanceIds);
        RebootInstancesResult result = ec2Client.rebootInstances(rebootInstancesRequest);

        List<String> ips =
                ec2Client.describeInstances(new DescribeInstancesRequest()
                        .withInstanceIds(instanceIds))
                        .getReservations()
                        .stream()
                        .map(Reservation::getInstances)
                        .flatMap(List::stream)
                        .map(Instance::getPublicIpAddress)
                        .collect(Collectors.toList());

        List<Future> verifyFutures =
                ips.stream().map(ip -> verifyInstance(ip, verifyRequest)).collect(Collectors.toList());

        Future<Void> future = Future.future();

        CompositeFuture.all(verifyFutures)
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        logger.info("Rebooted EC2 instances: " + instanceIds);
                        future.complete();
                    } else {
                        future.fail("Could not reboot EC2 instances: " + instanceIds + ": " + ar.cause());
                    }
                });

        return future;
    }

    Future<Boolean> verifyInstance(String ip, VerifyRequest verifyRequest) {
        long startTime = System.currentTimeMillis();

        Vertx vertx = Vertx.currentContext().owner();
        HttpUtils httpUtils = new HttpUtils(vertx);

        Future<Boolean> future = Future.future();
        vertx.setPeriodic(15*1000, id -> {
            logger.info("Verify restart instance triggered");
            httpUtils.get(ip, verifyRequest.getUri(), verifyRequest.getPort())
                    .setHandler(ar -> {
                        if (ar.succeeded()) {
                            future.complete(true);
                            vertx.cancelTimer(id);
                        } else if (System.currentTimeMillis() - startTime > VERIFY_TIMEOUT) {
                            vertx.cancelTimer(id);
                            future.fail("Time limit exceeded for verifying IP: " + ip);
                        }
                    });
        });

        return future;
    }

}
