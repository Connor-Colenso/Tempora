package com.colen.tempora.logging.loggers.block_change_logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.block.Block;

import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.GenericUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("blockId", "INTEGER", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("stackTrace", "TEXT", "NOT NULL"),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL")
        );
    }

    @Override
    public void threadedSaveEvent(BlockChangeQueueElement queueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName()
            + " (blockId, metadata, stackTrace, x, y, z, dimensionID, timestamp, closestPlayerUUID, closestPlayerDistance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
        pstmt.setInt(1, queueElement.blockID);
        pstmt.setInt(2, queueElement.metadata);
        pstmt.setString(3, queueElement.stackTrace);
        pstmt.setInt(4, (int) Math.round(queueElement.x));
        pstmt.setInt(5, (int) Math.round(queueElement.y));
        pstmt.setInt(6, (int) Math.round(queueElement.z));
        pstmt.setInt(7, queueElement.dimensionId);
        pstmt.setTimestamp(8, new Timestamp(queueElement.timestamp));
        pstmt.setString(9, queueElement.closestPlayerUUID);
        pstmt.setDouble(10, queueElement.closestPlayerDistance);
        pstmt.executeUpdate();
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.x = resultSet.getInt("x");
            queueElement.y = resultSet.getInt("y");
            queueElement.z = resultSet.getInt("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");
            queueElement.closestPlayerUUID = PlayerUtils.UUIDToName(resultSet.getString("closestPlayerUUID"));
            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");

            events.add(queueElement);
        }
        return events;
    }

    public void recordSetBlock(int x, int y, int z, Block blockIn, int metadataIn, int dimensionId, String modID) {
        final World world = MinecraftServer.getServer().worldServerForDimension(dimensionId);

        if (!isChunkPopulatedAt(world, x, z)) return;

        final BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = dimensionId;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.stackTrace = modID + " : " + GenericUtils.getCallingClassChain();
        queueElement.blockID = Block.getIdFromBlock(blockIn);
        queueElement.metadata = metadataIn;

        EntityPlayer closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        final List<EntityPlayer> playerList = world.playerEntities;

        if (!playerList.isEmpty()) {
            for (EntityPlayer player : playerList) {
                double distance = player.getDistanceSq(x, y, z);
                if (distance < closestDistance) {
                    closestPlayer = player;
                    closestDistance = distance;
                }
            }
        } else {
            closestDistance = 0;
        }

        String closestPlayerName = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : TemporaUtils.UNKNOWN_PLAYER_NAME;
        closestDistance = Math.sqrt(closestDistance); // Convert from square distance to actual distance

        queueElement.closestPlayerUUID = closestPlayerName;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

    public boolean isChunkPopulatedAt(World world, int blockX, int blockZ) {
        // Convert block coordinates to chunk coordinates
        int chunkX = blockX >> 4; // divide by 16
        int chunkZ = blockZ >> 4;

        // Get the chunk object
        Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);

        return chunk.isTerrainPopulated;
    }
}
