package com.colen.tempora.logging.loggers.block_change;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.LogWriteSafety;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    private boolean globalBlockChangeLogging;

    @Override
    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    @Override
    public String getSQLTableName() {
        return "BlockChangeLogger";
    }

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("blockID", "INTEGER", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL"),
            new ColumnDef("stackTrace", "TEXT", "NOT NULL"),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getSQLTableName(), false, """
            If true, overrides all regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);
    }

    @Override
    public void threadedSaveEvents(List<BlockChangeQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (blockID, metadata, pickBlockID, pickBlockMeta, stackTrace, x, y, z, dimensionID, timestamp, closestPlayerUUID, closestPlayerDistance) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (BlockChangeQueueElement queueElement : queueElements) {
                pstmt.setInt(1, queueElement.blockID);
                pstmt.setInt(2, queueElement.metadata);
                pstmt.setInt(3, queueElement.pickBlockID);
                pstmt.setInt(4, queueElement.pickBlockMeta);
                pstmt.setString(5, queueElement.stackTrace);
                pstmt.setInt(6, (int) Math.round(queueElement.x));
                pstmt.setInt(7, (int) Math.round(queueElement.y));
                pstmt.setInt(8, (int) Math.round(queueElement.z));
                pstmt.setInt(9, queueElement.dimensionId);
                pstmt.setTimestamp(10, new Timestamp(queueElement.timestamp));
                pstmt.setString(11, queueElement.closestPlayerUUID);
                pstmt.setDouble(12, queueElement.closestPlayerDistance);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @Override
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {
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
        World world;

        try {
            world = MinecraftServer.getServer()
                .worldServerForDimension(dimensionId);
        } catch (Exception e) {
            return;
        }

        // Only log changes if (x, y, z) is inside a defined region
        if (!globalBlockChangeLogging && !RegionRegistry.get(world)
            .containsBlock(dimensionId, x, y, z)) {
            return;
        }

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

        ItemStack pickStack = getPickBlockSafe(blockIn, world, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            queueElement.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            queueElement.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place‑block data
            queueElement.pickBlockID = queueElement.blockID;
            queueElement.pickBlockMeta = queueElement.metadata;
        }

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
