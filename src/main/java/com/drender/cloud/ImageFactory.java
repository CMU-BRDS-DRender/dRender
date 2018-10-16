package com.drender.cloud;

public class ImageFactory {

    public static String getImageAMI(String software) {
        switch (software) {
            case "blender":
            default:
                return "<ami-id>";
        }
    }
}
