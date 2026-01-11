package com.colen.tempora;

import static com.colen.tempora.Tempora.LOG;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.config.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class TemporaUtils {

    public static final String UNKNOWN_PLAYER_NAME = "tempora.unknown.player";
    public static final String UNKNOWN_CAUSE = "tempora.unknown.cause";
    public static final String ERROR = "tempora.unknown.uuid.to.player.error";

    public static Path databaseDir() {
        // Works for both dedicated and integrated servers.
        File worldDir = DimensionManager.getCurrentSaveRootDirectory();
        if (worldDir == null) throw new NullPointerException("worldDir is null");
        Path dir = worldDir.toPath()
            .resolve("TemporaDatabases");

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create database directory: " + dir, e);
        }
        return dir;
    }

    public static void deleteLoggerDatabase(String loggerName) {
        Path dbPath = databaseDir().resolve(loggerName + ".db");

        try {
            if (Files.exists(dbPath)) {
                Files.delete(dbPath);
                LOG.info("Deleted Tempora database: {}", dbPath.toString());
            } else {
                LOG.warn("Database file does not exist: {}", dbPath.toString());
            }
        } catch (IOException e) {
            LOG.error("Failed to delete Tempora database: {}", dbPath.toString(), e);
            throw new RuntimeException("Unable to delete database file: " + dbPath, e);
        }
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
}
