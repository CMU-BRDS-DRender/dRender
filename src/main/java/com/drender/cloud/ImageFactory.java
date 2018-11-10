package com.drender.cloud;

public class ImageFactory {

    public static String getJobImageAMI(String software) {
        switch (software) {
            case "blender":
            default:
                return "ami-064f1f98f25b73a81";
        }
    }
}
