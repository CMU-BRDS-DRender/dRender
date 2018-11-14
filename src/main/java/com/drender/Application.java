package com.drender;

import io.vertx.core.Launcher;

public class Application {
    public static void main(String[] args) {
        Launcher.executeCommand("run", MasterController.class.getName(),
                                "-Dvertx.options.blockedThreadCheckInterval=480000000000");
    }
}