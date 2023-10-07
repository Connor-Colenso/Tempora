package com.myname.mymodid;

import net.minecraft.server.MinecraftServer;

import java.io.File;

public class TemporaUtils {

    public static String databaseDirectory() {

        final String path = "./Saves/" + MinecraftServer.getServer().getFolderName() + "/TemporaDatabases/";

        // Create the directory if it doesn't exist.
        final File directory = new File(path);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                System.out.println("Directory was created successfully");
            } else {
                System.out.println("Failed to create the directory");
            }
        } else {
            System.out.println("Directory already exists");
        }

        return "jdbc:sqlite:" + path;
    }
}
