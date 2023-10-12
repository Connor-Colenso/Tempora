package com.myname.mymodid.Commands;

import static com.myname.mymodid.TemporaUtils.parseUnix;

import java.sql.*;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import com.myname.mymodid.TemporaUtils;

public class LastInDimension extends CommandBase {

    @Override
    public String getCommandName() {
        return "lastindim";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lastindim <dimension ID>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int dimensionID = parseInt(sender, args[0]);

        queryDatabase(sender, dimensionID);
    }

    private void queryDatabase(ICommandSender sender, int dimensionID) {
        try (Connection conn = DriverManager
            .getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE dimensionID = ? "
                + "ORDER BY timestamp DESC LIMIT 1";

            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, dimensionID);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String result = String.format(
                    "%s was last in dimension %d at [%d, %d, %d] on %s",
                    rs.getString("playerName"),
                    dimensionID,
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    parseUnix(rs.getString("timestamp")));
                sender.addChatMessage(new ChatComponentText(result + "."));
            } else {
                sender.addChatMessage(new ChatComponentText("No players have been in dimension " + dimensionID + "."));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }
}
