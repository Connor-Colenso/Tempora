package com.myname.mymodid.Commands;

import java.sql.*;

import com.myname.mymodid.MyMod;
import com.myname.mymodid.Network.TempName;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.myname.mymodid.TemporaUtils;

public class TrackPlayerCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "trackplayer";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/trackplayer <name>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String playerName = args[0];
        queryDatabase(sender, playerName);
    }

    private void queryDatabase(ICommandSender sender, String playerName) {
        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE playerName = ? AND ABS(x - ?) <= 100 AND ABS(y - ?) <= 100 AND ABS(z - ?) <= 100";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, playerName);
            pstmt.setDouble(2, sender.getPlayerCoordinates().posX);
            pstmt.setDouble(3, sender.getPlayerCoordinates().posY);
            pstmt.setDouble(4, sender.getPlayerCoordinates().posZ);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String result = String.format(
                    "%s was at [%f, %f, %f] on %s",
                    rs.getString("playerName"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("timestamp"));
                sender.addChatMessage(new ChatComponentText(result));

                sendTempNamePacket(sender.getEntityWorld(), playerName, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void sendTempNamePacket(World world, String playerName, double x, double y, double z) {
        TempName packet = new TempName(x, y, z);

        for (EntityPlayer player : world.playerEntities) {
            double distanceSquared = player.getDistanceSq(x, y, z);
            // If within 100 blocks
            if (distanceSquared < 10_000) MyMod.NETWORK.sendTo(packet, (EntityPlayerMP) player);
        }
    }


    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }
}
