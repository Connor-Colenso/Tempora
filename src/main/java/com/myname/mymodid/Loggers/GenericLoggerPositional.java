package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;

import com.myname.mymodid.TemporaUtils;
import net.minecraftforge.common.config.Configuration;

public abstract class GenericLoggerPositional {

    public abstract void handleConfig(Configuration config);

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static final Set<GenericLoggerPositional> loggerList = new HashSet<>();

    public static ArrayList<String> queryEventsWithinRadiusAndTime(ICommandSender sender, int radius, long seconds, String tableName) {

        ArrayList<String> returnList = new ArrayList<>();

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) return returnList;

        for (GenericLoggerPositional logger : GenericLoggerPositional.loggerList) {
            try {
                if (tableName != null) {
                    if (!logger.getTableName().equals(tableName)) continue;
                }

                // Construct the SQL query
                final String sql = "SELECT * FROM " + logger.getTableName()
                    + " WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ?"
                    + " AND dimensionID = ? AND timestamp >= datetime(?, 'unixepoch')";

                // Prepare the statement
                PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
                pstmt.setInt(1, sender.getPlayerCoordinates().posX);
                pstmt.setInt(2, radius);
                pstmt.setInt(3, sender.getPlayerCoordinates().posY);
                pstmt.setInt(4, radius);
                pstmt.setInt(5, sender.getPlayerCoordinates().posZ);
                pstmt.setInt(6, radius);
                pstmt.setInt(7, entityPlayerMP.dimension);
                pstmt.setLong(8, System.currentTimeMillis() / 1000 - seconds);

                // Execute the query
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    returnList.add(logger.processResultSet(rs));
                }
            } catch (SQLException e) {
                returnList.add("Database query failed on " + logger.getTableName() + ". " + e.getLocalizedMessage());
            }
        }

        return returnList;
    }

    protected abstract String processResultSet(ResultSet rs) throws SQLException;

    protected static Connection positionLoggerDBConnection;

    public GenericLoggerPositional() {
        MinecraftForge.EVENT_BUS.register(this);
        loggerList.add(this);
    }

    public static void onServerStart() {
        try {
            positionLoggerDBConnection = DriverManager
                .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");

            for (GenericLoggerPositional loggerPositional : loggerList) {
                loggerPositional.initTable();
            }

        } catch (SQLException sqlException) {
            System.err.println("Critical exception, could not open Tempora databases properly.");
            sqlException.printStackTrace();
        }
    }

    public static void onServerClose() {
        try { // Todo lock this properly.
            positionLoggerDBConnection.close();
        } catch (SQLException exception) {
            System.err.println("Critical exception, could not close Tempora databases properly.");
            exception.printStackTrace();
        }
    }

    public abstract void initTable();

    public final String getTableName() {
        return getClass().getSimpleName();
    }

}
