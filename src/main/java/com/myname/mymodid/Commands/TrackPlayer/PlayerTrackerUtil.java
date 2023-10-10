package com.myname.mymodid.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.myname.mymodid.TemporaUtils;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import com.myname.mymodid.Network.PlayerPositionPacket;
import com.myname.mymodid.Tempora;

public class PlayerTrackerUtil {

    private static final int MAX_POINTS_PER_PACKET = 500;

    public static void queryAndSendDataToPlayer(ICommandSender sender, String playerName) {
        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            int renderDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance() * 16; // 16 blocks per chunk

            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE playerName = ? AND ABS(x - ?) <= " + renderDistance + " AND ABS(z - ?) <= " + renderDistance;
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, playerName);
            pstmt.setInt(2, sender.getPlayerCoordinates().posX);
            pstmt.setInt(3, sender.getPlayerCoordinates().posZ);

            ResultSet rs = pstmt.executeQuery();

            List<Integer> xs = new ArrayList<>();
            List<Integer> ys = new ArrayList<>();
            List<Integer> zs = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();

            while (rs.next()) {
                xs.add(rs.getInt("x"));
                ys.add(rs.getInt("y"));
                zs.add(rs.getInt("z"));
                timestamps.add(rs.getLong("timestamp"));
            }

            EntityPlayerMP player = (EntityPlayerMP) sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());

            boolean firstPacket = true;
            for (int i = 0; i < xs.size(); i += MAX_POINTS_PER_PACKET) {
                int endIndex = Math.min(i + MAX_POINTS_PER_PACKET, xs.size());
                boolean lastPacket = endIndex == xs.size();

                int[] xArray = xs.subList(i, endIndex).stream().mapToInt(Integer::intValue).toArray();
                int[] yArray = ys.subList(i, endIndex).stream().mapToInt(Integer::intValue).toArray();
                int[] zArray = zs.subList(i, endIndex).stream().mapToInt(Integer::intValue).toArray();
                long[] timeArray = timestamps.subList(i, endIndex).stream().mapToLong(Long::longValue).toArray();

                PlayerPositionPacket packet = new PlayerPositionPacket(xArray, yArray, zArray, timeArray, firstPacket, lastPacket);
                Tempora.NETWORK.sendTo(packet, player);

                firstPacket = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
