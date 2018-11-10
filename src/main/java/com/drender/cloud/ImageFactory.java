package com.drender.cloud;

public class ImageFactory {

    public static String getJobImageAMI(String software) {
        switch (software) {
            case "blender":
            default:
                return "ami-069aa902e21369609";
        }
    }
}
