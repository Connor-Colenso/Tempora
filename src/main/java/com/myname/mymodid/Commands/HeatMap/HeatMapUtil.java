package com.myname.mymodid.Commands.HeatMap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacket;
import com.myname.mymodid.Tempora;
import com.myname.mymodid.TemporaUtils;

import codechicken.lib.vec.BlockCoord;

public class HeatMapUtil {

    private static final int MAX_POINTS_PER_PACKET = 500;

    public static void queryAndSendDataToPlayer(ICommandSender sender, long maxTimeToLookBackInSeconds,
        String playerName) {
        try (Connection conn = DriverManager
            .getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {

            int renderDistance = MinecraftServer.getServer()
                .getConfigurationManager()
                .getViewDistance() * 16; // 16 blocks per chunk

            // Calculate the minimum timestamp based on maxTimeToLookBackInSeconds
            long currentTimestamp = System.currentTimeMillis();
            long minTimestamp = currentTimestamp - maxTimeToLookBackInSeconds * 1000; // Convert
                                                                                      // maxTimeToLookBackInSeconds to
                                                                                      // milliseconds

            // Update the SQL query to incorporate the timestamp filter
            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
                + "WHERE playerName = ? AND ABS(x - ?) <= "
                + renderDistance
                + " AND ABS(z - ?) <= "
                + renderDistance
                + " AND timestamp >= ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setFetchSize(1000);
            pstmt.setString(1, playerName);
            pstmt.setDouble(2, sender.getPlayerCoordinates().posX);
            pstmt.setDouble(3, sender.getPlayerCoordinates().posZ);
            pstmt.setLong(4, minTimestamp);

            ResultSet rs = pstmt.executeQuery();

            Map<BlockCoord, Double> pointIntensity = new HashMap<>();
            while (rs.next()) {
                int x = (int) Math.round(rs.getDouble("x"));
                int y = (int) Math.round(rs.getDouble("y"));
                int z = (int) Math.round(rs.getDouble("z"));
                BlockCoord coord = new BlockCoord(x, y, z);
                pointIntensity.put(coord, pointIntensity.getOrDefault(coord, 0.0) + 1.0);
            }

            // Get the maximum intensity from the map
            double maxIntensity = Collections.max(pointIntensity.values());

            // Normalize each intensity value in the map by the maximum intensity
            pointIntensity.replaceAll((k, v) -> pointIntensity.get(k) / maxIntensity);

            // Operator who issued the command.
            EntityPlayerMP operator = (EntityPlayerMP) sender.getEntityWorld()
                .getPlayerEntityByName(sender.getCommandSenderName());

            List<Map.Entry<BlockCoord, Double>> entries = new ArrayList<>(pointIntensity.entrySet());

            for (int i = 0; i < entries.size(); i += MAX_POINTS_PER_PACKET) {
                int endIndex = Math.min(i + MAX_POINTS_PER_PACKET, entries.size());

                int[] xArray = new int[endIndex - i];
                int[] yArray = new int[endIndex - i];
                int[] zArray = new int[endIndex - i];
                double[] intensityArray = new double[endIndex - i];

                for (int j = i, idx = 0; j < endIndex; j++, idx++) {
                    Map.Entry<BlockCoord, Double> entry = entries.get(j);
                    BlockCoord coord = entry.getKey();
                    xArray[idx] = coord.x;
                    yArray[idx] = coord.y;
                    zArray[idx] = coord.z;
                    intensityArray[idx] = entry.getValue();
                }

                boolean isFirstPacket = i == 0;
                boolean isLastPacket = endIndex >= entries.size();

                HeatMapPacket packet = new HeatMapPacket(
                    xArray,
                    yArray,
                    zArray,
                    intensityArray,
                    isFirstPacket,
                    isLastPacket);

                Tempora.NETWORK.sendTo(packet, operator);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
