package com.drender.cloud.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.drender.cloud.MachineProvider;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.cloud.DrenderInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AWSProvider implements MachineProvider<AWSRequestProperty>{

    private AWSStaticCredentialsProvider credentialProvider;
    private EC2Provisioner ec2Provisioner;
    private ArrayList<String> instanceIds;
    private ArrayList<String> spotInstanceRequestIds;


    public AWSProvider(){
        HashMap<String, String> localAWSCredentials = Credentials.getCredentials();
        AWSCredentials credentials = null;
        try {
            credentials = new BasicAWSCredentials(localAWSCredentials.get("AWS_ACCESS_KEY_ID"), localAWSCredentials.get("AWS_ACCESS_KEY_SECRET"));
            credentialProvider = new AWSStaticCredentialsProvider(credentials);
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file.Please make sure that your credentials file is at the correct ",
                    e);
        }
    }

    @Override
    public List<DrenderInstance> startMachines(AWSRequestProperty property) {
        ec2Provisioner = new EC2Provisioner(property.getRegion(), credentialProvider);
        return ec2Provisioner.spawnInstances(property.getNameList(),property.getSecurityGroup(),property.getSshKeyName(),property.getMachineImageId());
    }

    @Override
    public String machineStatus(DrenderInstance instance) {
        return null;
    }

    @Override
    public boolean killMachine(DrenderInstance instance) {
        return false;
    }

}
