package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.loggers.generic.ModpackVersionData.CURRENT_VERSION;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import com.colen.tempora.loggers.generic.GenericQueueElement;

public class DatabaseUtils {

    public static final String MISSING_STRING_DATA = "MISSING_DATA";

    public static void defaultColumnEntries(GenericQueueElement queueElement, PreparedStatement pstmt, int index)
        throws SQLException {
        pstmt.setString(index++, queueElement.eventID);
        pstmt.setDouble(index++, queueElement.x);
        pstmt.setDouble(index++, queueElement.y);
        pstmt.setDouble(index++, queueElement.z);
        pstmt.setInt(index++, queueElement.dimensionID);
        pstmt.setTimestamp(index++, new Timestamp(queueElement.timestamp));

        // This is a static and unchanging int that corresponds to a modpack version. It can only shift on server
        // restart to indicate the modding environment has changed.
        pstmt.setInt(index, CURRENT_VERSION);
    }

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
