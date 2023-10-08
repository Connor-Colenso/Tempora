package com.myname.mymodid.Utils;

import java.sql.*;

import com.myname.mymodid.TemporaUtils;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import com.myname.mymodid.Network.PlayerPositionPacket;
import com.myname.mymodid.Tempora;

public class PlayerTrackerUtil {

    public static void queryAndSendDataToPlayer(ICommandSender sender, String playerName) {
        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            int renderDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance() * 16; // 16 blocks per chunk

            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE playerName = ? AND ABS(x - ?) <= " + renderDistance + " AND ABS(z - ?) <= " + renderDistance;
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, playerName);
            pstmt.setDouble(2, sender.getPlayerCoordinates().posX);
            pstmt.setDouble(3, sender.getPlayerCoordinates().posZ);

            ResultSet rs = pstmt.executeQuery();

            boolean firstPacket = true;
            while (rs.next()) {
                EntityPlayerMP player = (EntityPlayerMP) sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());

                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                long timestamp = rs.getLong("timestamp");

                PlayerPositionPacket packet = new PlayerPositionPacket(x, y, z, timestamp, firstPacket);
                Tempora.NETWORK.sendTo(packet, player);
                firstPacket = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
