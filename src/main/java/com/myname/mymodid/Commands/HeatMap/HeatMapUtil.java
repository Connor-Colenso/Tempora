package com.myname.mymodid.Commands.HeatMap;

import codechicken.lib.vec.BlockCoord;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacket;
import com.myname.mymodid.Tempora;
import com.myname.mymodid.TemporaUtils;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeatMapUtil {

    private static final int MAX_POINTS_PER_PACKET = 500;

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

            Map<BlockCoord, Double> pointIntensity = new HashMap<>();
            while (rs.next()) {
                int x = (int) Math.round(rs.getDouble("x"));
                int y = (int) Math.round(rs.getDouble("y"));
                int z = (int) Math.round(rs.getDouble("z"));
                BlockCoord coord = new BlockCoord(x, y, z);
                pointIntensity.put(coord, pointIntensity.getOrDefault(coord, 0.0) + 1.0);
            }

            // Get the maximum intensity from the map
            double maxIntensity = pointIntensity.values().stream().mapToDouble(Double::doubleValue).sum();

            // Normalize each intensity value in the map by the maximum intensity
            pointIntensity.replaceAll((k, v) -> pointIntensity.get(k) / maxIntensity);

            // Operator who issued the command.
            EntityPlayerMP operator = (EntityPlayerMP) sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());

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

                HeatMapPacket packet = new HeatMapPacket(xArray, yArray, zArray, intensityArray, isFirstPacket, isLastPacket);

                Tempora.NETWORK.sendTo(packet, operator);
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
