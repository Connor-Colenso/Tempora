package com.myname.mymodid;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import java.sql.*;
import java.util.concurrent.TimeUnit;

public class QueryEventsCommand extends CommandBase {
    private static final String DB_URL = "jdbc:sqlite:./blockBreakEvents.db";

    @Override
    public String getCommandName() {
        return "queryevents";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/queryevents <radius> <time>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int radius = parseInt(sender, args[0]);
        long seconds = parseTime(args[1]);

        queryDatabase(sender, radius, seconds);
    }

    private long parseTime(String time) {
        char timeSpecifier = time.charAt(time.length() - 1);
        int value = Integer.parseInt(time.substring(0, time.length() - 1));

        return switch (timeSpecifier) {
            case 's' -> value;
            case 'm' -> TimeUnit.MINUTES.toSeconds(value);
            case 'h' -> TimeUnit.HOURS.toSeconds(value);
            case 'd' -> TimeUnit.DAYS.toSeconds(value);
            default -> throw new IllegalArgumentException("Invalid time format.");
            // Needs better handling.
        };
    }

    private void queryDatabase(ICommandSender sender, int radius, long seconds) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT playerName, blockType, x, y, z, timestamp FROM BlockBreakEvents " +
                "WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ? AND " +
                "timestamp >= datetime(CURRENT_TIMESTAMP, ? || ' seconds')";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, sender.getPlayerCoordinates().posX);
            pstmt.setInt(2, radius);
            pstmt.setInt(3, sender.getPlayerCoordinates().posY);
            pstmt.setInt(4, radius);
            pstmt.setInt(5, sender.getPlayerCoordinates().posZ);
            pstmt.setInt(6, radius);
            pstmt.setLong(7, -seconds);  // Negate seconds for the "ago" behavior.

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String result = String.format("%s broke %s at [%d, %d, %d] on %s",
                    rs.getString("playerName"), rs.getString("blockType"), rs.getInt("x"), rs.getInt("y"),
                    rs.getInt("z"), rs.getString("timestamp"));
                sender.addChatMessage(new ChatComponentText(result));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;  // Require OP permission level.
    }
}
