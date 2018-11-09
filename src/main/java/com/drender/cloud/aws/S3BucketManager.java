package com.drender.cloud.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.drender.cloud.StorageProvider;
import com.drender.model.cloud.S3Source;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class S3BucketManager implements StorageProvider {

    private final static String DEFAULT_BUCKET_NAME = "drender";
    private final String SUFFIX = "/";

    private AmazonS3 client;

    public S3BucketManager(){
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
            AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
            client = AmazonS3ClientBuilder.standard().withCredentials(credentialsProvider).build();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file.Please make sure that your credentials file is at the correct ",
                    e);
        }
    }

    @Override
    public S3Source createStorage(String folderName) {

        // Create folder with this projectID
        // create meta-data for your folder and set content-length to 0
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(0);

        // create empty content
        InputStream emptyContent = new ByteArrayInputStream(new byte[0]);

        String fileName = folderName + SUFFIX;

        // create a PutObjectRequest passing the folder name suffixed by /
        PutObjectRequest putObjectRequest = new PutObjectRequest(S3BucketManager.DEFAULT_BUCKET_NAME,
                folderName + SUFFIX, emptyContent, metadata);

        client.putObject(putObjectRequest);

        return new S3Source(S3BucketManager.DEFAULT_BUCKET_NAME, fileName);
    }

    @Override
    public boolean checkExists(S3Source source) {
        return client.doesObjectExist(source.getBucketName(), source.getFile());
    }
}
