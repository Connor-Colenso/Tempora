package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.NotNull;

public abstract class GenericLoggerPositional {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static final List<Connection> databaseList = new ArrayList<>();
    public static final List<GenericLoggerPositional> loggerList = new ArrayList<>();

        public ArrayList<String> queryEventsWithinRadiusAndTime(ICommandSender sender, int radius, long seconds) {

            ArrayList<String> returnList = new ArrayList<>();

            if (!(sender instanceof EntityPlayerMP entityPlayerMP)) return returnList;

            try {
                // Construct the SQL query
                final String sql = "SELECT * FROM Events"
                    + " WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ?"
                    + " AND dimensionID = ? AND timestamp >= datetime(?, 'unixepoch')";

                // Prepare the statement
                PreparedStatement pstmt = conn.prepareStatement(sql);
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
                    returnList.add(processResultSet(rs));
                }
            } catch (SQLException e) {
                returnList.add("Database query failed. " + e.getLocalizedMessage());
            }

        return returnList;
    }

    protected abstract String processResultSet(ResultSet rs) throws SQLException;

    public Connection getDatabaseConnection() {
        return conn;
    }

    protected Connection conn;

    public GenericLoggerPositional() {
        MinecraftForge.EVENT_BUS.register(this);
        loggerList.add(this);
    }

    public static void onServerStart() {
        for (@NotNull final GenericLoggerPositional logger : loggerList) {
            try {
                Connection conn = logger.initDatabase();
                databaseList.add(conn);
            } catch (Exception exception) {
                System.out.println("Critical exception, could not open Tempora databases properly.");
                exception.printStackTrace();
            }

        }
    }

    public static void onServerClose() {
        for (@NotNull final Connection conn : databaseList) {
            try {
                conn.close();
            } catch (SQLException exception) {
                System.out.println("Critical exception, could not close Tempora databases properly.");
                exception.printStackTrace();
            }
        }
    }

    public abstract Connection initDatabase();

    protected abstract String databaseURL();

}
