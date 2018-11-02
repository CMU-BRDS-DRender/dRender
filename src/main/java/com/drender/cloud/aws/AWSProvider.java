package com.drender.cloud.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.*;
import com.drender.cloud.MachineProvider;
import com.drender.model.cloud.AWSRequestProperty;
import com.drender.model.instance.DRenderInstance;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AWSProvider implements MachineProvider<AWSRequestProperty>{

    private AWSCredentialsProvider credentialProvider;
    private EC2Provisioner ec2Provisioner;

    public AWSProvider(){
        try {
            /*
             * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
             *
             * AWS credentials provider chain that looks for credentials in this order:
             *   1. Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
             *   2. Java System Properties - aws.accessKeyId and aws.secretKey
             *   3. Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
             *   4. Credentials delivered through the Amazon EC2 container service if AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"
             *       environment variable is set and security manager has permission to access the variable
             *   5. Instance profile credentials delivered through the Amazon EC2 metadata service
             */
            credentialProvider = new DefaultAWSCredentialsProviderChain();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file.Please make sure that your credentials file is at the correct ",
                    e);
        }
    }


    @Override
    public List<DRenderInstance> startMachines(AWSRequestProperty property) {
        ec2Provisioner = new EC2Provisioner(property.getRegion(), credentialProvider);
        try {
            List<DRenderInstance> instances =
                    ec2Provisioner.spawnInstances(property.getCount(),
                            property.getSecurityGroup(),
                            property.getSshKeyName(),
                            property.getMachineImageId());
            return instances;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    @Override
    public String machineStatus(DRenderInstance instance) {
        return null;
    }

    @Override
    public boolean killMachine(DRenderInstance instance) {
        return false;
    }

}
