package com.myname.mymodid;

import java.io.File;

import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class TemporaUtils {

    public static String databaseDirectory() {

        final String path = "./Saves/" + MinecraftServer.getServer()
            .getFolderName() + "/TemporaDatabases/";

        // Create the directory if it doesn't exist.
        final File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        return "jdbc:sqlite:" + path;
    }

    /**
     * Determines if the current environment is client-side.
     *
     * @return True if on the client side, false otherwise.
     */
    public static boolean isClientSide() {
        return FMLCommonHandler.instance()
            .getEffectiveSide() == Side.CLIENT;
    }
}
