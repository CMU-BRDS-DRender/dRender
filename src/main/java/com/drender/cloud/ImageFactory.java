package com.drender.cloud;

public class ImageFactory {

    public static String getImageAMI(String software) {
        switch (software) {
            case "blender":
            default:
                return "ami-0ac019f4fcb7cb7e6";
        }
    }
}
