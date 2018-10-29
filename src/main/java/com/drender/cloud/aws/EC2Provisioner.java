package com.drender.cloud.aws;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import com.drender.model.instance.DRenderInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EC2Provisioner {

    private static AmazonEC2 ec2Client = null;

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


    public List<DRenderInstance> spawnInstances(List<String> nameList, String securityGroup, String sshKeyName, String imageID) {

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(imageID)
                .withInstanceType(InstanceType.T1Micro)
                .withMinCount(nameList.size())
                .withMaxCount(nameList.size())
                .withKeyName(sshKeyName)
                .withSecurityGroups(securityGroup);

        RunInstancesResult result = ec2Client.runInstances(runInstancesRequest);

        List<Instance> instanceList = result.getReservation().getInstances();
        String[] instanceIds = new String[nameList.size()];
        instanceIds = instanceList.stream().map(Instance::getInstanceId).collect(Collectors.toList()).toArray(instanceIds);

        DescribeInstanceStatusRequest waitRequest = new DescribeInstanceStatusRequest().withInstanceIds(instanceIds);

        ec2Client.waiters().instanceStatusOk().run(new WaiterParameters<DescribeInstanceStatusRequest>().withRequest(waitRequest));

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
            String name = nameList.get(i);
            Tag nameTag = new Tag("Name", name);
            List<Tag> tagList = new ArrayList<>();
            tagList.add(nameTag);
            instance.setTags(tagList);
            DRenderInstanceList.add(new DRenderInstance(instance.getInstanceId(),instance.getPublicIpAddress()));
        }

        return DRenderInstanceList;
    }

}
