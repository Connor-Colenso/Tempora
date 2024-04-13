package com.colen.tempora;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.Config.Config;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

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

    public static String defaultDimID() {
        return "999";
    }

    public static long parseTime(String time) {
        char timeSpecifier = time.charAt(time.length() - 1);
        int value = Integer.parseInt(time.substring(0, time.length() - 1));

        return switch (timeSpecifier) {
            case 's' -> value;
            case 'm' -> TimeUnit.MINUTES.toSeconds(value);
            case 'h' -> TimeUnit.HOURS.toSeconds(value);
            case 'd' -> TimeUnit.DAYS.toSeconds(value);
            default -> throw new IllegalArgumentException("Invalid time format provided.");
            // Needs better handling.
        };
    }

    // Todo localise these depending on users date.
    // Unix epoch in miliseconds -> Date
    public static String parseUnix(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        return zdt.format(DateTimeFormatter.ISO_INSTANT);
    }

    public static String parseUnix(String timestamp) {
        return parseUnix(Long.parseLong(timestamp));
    }
}
