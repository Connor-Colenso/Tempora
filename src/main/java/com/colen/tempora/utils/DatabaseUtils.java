package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.minecraftforge.common.DimensionManager;

public class DatabaseUtils {

    public static final String MISSING_STRING_DATA = "MISSING_DATA";

    @SuppressWarnings("LoggingSimilarMessage")
    public static boolean isDatabaseCorrupted(Connection conn) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA integrity_check;")) {

            if (!rs.next()) return true;

            String result = rs.getString(1);

            if (!"ok".equalsIgnoreCase(result)) {
                LOG.error("################################################################################");
                LOG.error("#                           TEMPORA DATABASE ERROR                             #");
                LOG.error("################################################################################");
                LOG.error("# A critical SQLite integrity check has FAILED.                                #");
                LOG.error("# The database is CORRUPTED and cannot be safely used.                         #");
                LOG.error("#                                                                              #");
                LOG.error("# Integrity check result: {}                                                   #", result);
                LOG.error("################################################################################");

                return true;
            }
            return false;

        } catch (SQLException e) {
            LOG.error("################################################################################");
            LOG.error("#                           TEMPORA DATABASE ERROR                             #");
            LOG.error("################################################################################");
            LOG.error("# SQLite integrity check could not be executed.                                #");
            LOG.error("# The database file is unreadable or corrupted.                                #");
            LOG.error("################################################################################");
            LOG.error("Underlying SQL exception:", e);

            return true;
        }
    }

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
}
