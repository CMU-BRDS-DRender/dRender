package com.drender.cloud.aws;

import java.util.ArrayList;
import java.util.HashMap;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

public class EC2Provisioner {

    private AmazonEC2 ec2;
    private ArrayList<String> instanceIds;
    private ArrayList<String> spotInstanceRequestIds;
    private AWSStaticCredentialsProvider credentialProvider;
    private String region;

    public EC2Provisioner(String region) throws Exception {

        HashMap<String, String> localAWSCredentials = Credentials.getCredentials();

        AWSCredentials credentials = null;
        try {
            credentials = new BasicAWSCredentials(localAWSCredentials.get("AWS_ACCESS_KEY_ID"),localAWSCredentials.get("AWS_ACCESS_KEY_SECRET"));
            credentialProvider = new AWSStaticCredentialsProvider(credentials);
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file.Please make sure that your credentials file is at the correct "
            e);
        }

        this.region = region;
        ec2 = AmazonEC2ClientBuilder.standard().withCredentials(credentialProvider)
                .withRegion(region)
                .build();

    }


    public void spawnInstances(String securityGroup, String sshKeyName,String imageID, int count){
        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(imageID)
                .withInstanceType(InstanceType.T1Micro)
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(sshKeyName)
                .withSecurityGroups(securityGroup);

        RunInstancesResult result = ec2.runInstances(
                runInstancesRequest);
    }

}
