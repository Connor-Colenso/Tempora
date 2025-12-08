package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
}
