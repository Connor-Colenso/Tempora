package com.myname.mymodid.Commands.TrackPlayer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacket;
import com.myname.mymodid.Tempora;
import com.myname.mymodid.TemporaUtils;

public class PlayerTrackerUtil {

    private static final int MAX_POINTS_PER_PACKET = 500;

    public static void queryAndSendDataToPlayer(ICommandSender sender, String playerName) {
        try (Connection conn = DriverManager
            .getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            int renderDistance = MinecraftServer.getServer()
                .getConfigurationManager()
                .getViewDistance() * 16; // 16 blocks per chunk

            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE playerName = ? AND ABS(x - ?) <= "
                + renderDistance
                + " AND ABS(z - ?) <= "
                + renderDistance;
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, playerName);
            pstmt.setInt(2, sender.getPlayerCoordinates().posX);
            pstmt.setInt(3, sender.getPlayerCoordinates().posZ);

            ResultSet rs = pstmt.executeQuery();

            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            List<Double> zs = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();

            while (rs.next()) {
                xs.add(rs.getDouble("x"));
                ys.add(rs.getDouble("y"));
                zs.add(rs.getDouble("z"));
                timestamps.add(rs.getLong("timestamp"));
            }

            EntityPlayerMP player = (EntityPlayerMP) sender.getEntityWorld()
                .getPlayerEntityByName(sender.getCommandSenderName());

            boolean firstPacket = true;
            for (int i = 0; i < xs.size(); i += MAX_POINTS_PER_PACKET) {
                int endIndex = Math.min(i + MAX_POINTS_PER_PACKET, xs.size());
                boolean lastPacket = endIndex == xs.size();

                double[] xArray = xs.subList(i, endIndex)
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                double[] yArray = ys.subList(i, endIndex)
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                double[] zArray = zs.subList(i, endIndex)
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                long[] timeArray = timestamps.subList(i, endIndex)
                    .stream()
                    .mapToLong(Long::longValue)
                    .toArray();

                PlayerPositionPacket packet = new PlayerPositionPacket(
                    xArray,
                    yArray,
                    zArray,
                    timeArray,
                    firstPacket,
                    lastPacket);
                Tempora.NETWORK.sendTo(packet, player);

                firstPacket = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
