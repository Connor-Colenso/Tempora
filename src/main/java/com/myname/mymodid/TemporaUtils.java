package com.myname.mymodid;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
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

    public static boolean isServerSide() {
        return FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER;
    }

    /**
     * Determines if the current environment is client-side.
     *
     * @return True if on the client side, false otherwise.
     */
    public static boolean isClientSide() {
        return FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT;
    }
}
