package com.colen.tempora;

import com.colen.tempora.Config.Config;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TemporaUtils {

    public static final String UNKNOWN_PLAYER_NAME = "[UNKNOWN]";

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

    public static boolean isServerSide() {
        return !isClientSide();
    }

    public static boolean shouldTemporaRun() {
        return isServerSide() || Config.shouldTemporaRun;
    }

    // Unix epoch in miliseconds -> Date string
    public static String parseUnix(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        return zdt.format(DateTimeFormatter.ISO_INSTANT);
    }

    public static String parseUnix(String timestamp) {
        return parseUnix(Long.parseLong(timestamp));
    }

    public static void process(C0EPacketClickWindow packet, EntityPlayerMP player) {
        System.out.println(player.getDisplayName() + " " + packet.func_149546_g().getUnlocalizedName());
    }
}
