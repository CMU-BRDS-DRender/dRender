package com.drender.cloud.aws;

import java.util.HashMap;

public class Credentials {

    static HashMap<String, String> getCredentials(){

        HashMap<String, String> credentialMap = new HashMap<String, String>();

        String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
        String AWS_ACCESS_KEY_SECRET = System.getenv("AWS_ACCESS_KEY_SECRET");

        credentialMap.put("AWS_ACCESS_KEY_ID", AWS_ACCESS_KEY_ID);
        credentialMap.put("AWS_ACCESS_KEY_SECRET", AWS_ACCESS_KEY_SECRET);

        return credentialMap;
    }
}
