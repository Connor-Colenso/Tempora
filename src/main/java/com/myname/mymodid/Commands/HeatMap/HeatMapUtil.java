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

        try (Connection conn = createDatabaseConnection()) {
            List<Map.Entry<BlockCoord, Double>> entries = fetchData(
                sender,
                maxTimeToLookBackInSeconds,
                playerName,
                conn);
            sendHeatMapDataToPlayer(entries, sender);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Connection createDatabaseConnection() throws SQLException {
        return DriverManager.getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db");
    }

    private static List<Map.Entry<BlockCoord, Double>> fetchData(ICommandSender sender, long maxTimeToLookBackInSeconds,
        String playerName, Connection conn) throws SQLException {

        int renderDistance = MinecraftServer.getServer()
            .getConfigurationManager()
            .getViewDistance() * 16; // 16 blocks per chunk

        long currentTimestamp = System.currentTimeMillis();
        long minTimestamp = currentTimestamp - maxTimeToLookBackInSeconds * 1000;

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

        double maxIntensity = Collections.max(pointIntensity.values());
        pointIntensity.replaceAll((k, v) -> pointIntensity.get(k) / maxIntensity);

        return new ArrayList<>(pointIntensity.entrySet());
    }

    private static void sendHeatMapDataToPlayer(List<Map.Entry<BlockCoord, Double>> entries, ICommandSender sender) {
        EntityPlayerMP operator = (EntityPlayerMP) sender.getEntityWorld()
            .getPlayerEntityByName(sender.getCommandSenderName());

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
    }
}
