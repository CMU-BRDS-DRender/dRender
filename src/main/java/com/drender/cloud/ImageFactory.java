package com.drender.cloud;

public class ImageFactory {

    public static String getJobImageAMI(String software) {
        switch (software) {
            case "blender":
            default:
                return "ami-0cc9279ae9aec521e";
        }
    }
}
