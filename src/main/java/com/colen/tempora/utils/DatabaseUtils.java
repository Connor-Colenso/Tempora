package com.colen.tempora.utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import cpw.mods.fml.common.FMLLog;

public class DatabaseUtils {

    public static final String MISSING_STRING_DATA = "MISSING_DATA";

    public static boolean isDatabaseCorrupted(Connection conn) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA integrity_check;")) {

            if (!rs.next()) return true;

            String result = rs.getString(1);

            if (!"ok".equalsIgnoreCase(result)) {
                FMLLog.severe("################################################################################");
                FMLLog.severe("#                           TEMPORA DATABASE ERROR                             #");
                FMLLog.severe("################################################################################");
                FMLLog.severe("# A critical SQLite integrity check has FAILED.                                #");
                FMLLog.severe("# The database is CORRUPTED and cannot be safely used.                         #");
                FMLLog.severe("#                                                                              #");
                FMLLog.severe("# Integrity check result: %s", result);
                FMLLog.severe("################################################################################");

                return true;
            }

            return false;

        } catch (SQLException e) {

            FMLLog.severe("################################################################################");
            FMLLog.severe("#                           TEMPORA DATABASE ERROR                             #");
            FMLLog.severe("################################################################################");
            FMLLog.severe("# SQLite integrity check could not be executed.                                #");
            FMLLog.severe("# The database file is unreadable or corrupted.                                #");
            FMLLog.severe("################################################################################");
            FMLLog.severe("Underlying SQL exception:", e);

            return true;
        }
    }
}
