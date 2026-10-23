package com.colen.tempora.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.common.DimensionManager;

public class DatabaseUtils {

    public static final String MISSING_STRING_DATA = "MISSING_DATA";

    public static Path databaseDir() {
        // Works for both dedicated and integrated servers.
        File worldDir = DimensionManager.getCurrentSaveRootDirectory();
        if (worldDir == null) throw new NullPointerException("worldDir is null");
        Path dir = worldDir.toPath()
            .resolve("tempora_databases");

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
}
