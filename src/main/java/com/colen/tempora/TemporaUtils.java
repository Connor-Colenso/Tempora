package com.colen.tempora;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.config.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class TemporaUtils {

    public static final String UNKNOWN_PLAYER_NAME = "[UNKNOWN]";

    public static Path databaseDir() {
        // Works for both dedicated and integrated servers.
        Path worldDir = DimensionManager.getCurrentSaveRootDirectory()
            .toPath();
        Path dir = worldDir.resolve("TemporaDatabases");

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create database directory: " + dir, e);
        }
        return dir;
    }

    /** Absolute JDBC URL for the given database file (e.g. "blocks.db"). */
    public static String jdbcUrl(String fileName) {
        return "jdbc:sqlite:" + databaseDir().resolve(fileName)
            .toAbsolutePath();
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
        System.out.println(
            player.getDisplayName() + " "
                + packet.func_149546_g()
                    .getUnlocalizedName());
    }
}
