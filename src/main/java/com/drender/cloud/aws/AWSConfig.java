package com.drender.cloud.aws;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AWSConfig {
    private static final String propertiesFile = "awsconfig.properties";

    @Getter
    private static String region;
    @Getter
    private static String securityGroup;
    @Getter
    private static String sshKeyName;
    @Getter
    private static String instanceType;

    static {
        Properties properties = new Properties();

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream resourceStream = loader.getResourceAsStream(propertiesFile)) {
            properties.load(resourceStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        region = properties.getProperty("aws.region");
        securityGroup = properties.getProperty("aws.securityGroup");
        sshKeyName = properties.getProperty("aws.sshKeyName");
        instanceType = properties.getProperty("aws.instanceType");
    }
}
