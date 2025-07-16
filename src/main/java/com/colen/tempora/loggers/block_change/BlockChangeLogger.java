package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.nbt.NBTConverter.NO_NBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.utils.nbt.NBTConverter;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    private boolean globalBlockChangeLogging;
    private boolean logNBT;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.BlockChangeLogger;
    }

    @Override
    public void renderEventsInWorld(RenderWorldLastEvent e) {

    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("blockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("stackTrace", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("encodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getSQLTableName(), false, """
            If true, overrides all custom regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);

        logNBT = config.getBoolean("logNBT", getSQLTableName(), false, """
            If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
            WARNING: NBT may be large and this could cause the database to grow much quicker.
            """);
    }

    @Override
    public void threadedSaveEvents(List<BlockChangeQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (blockID, metadata, pickBlockID, pickBlockMeta, stackTrace, encodedNBT, x, y, z, dimensionID, timestamp, closestPlayerUUID, closestPlayerDistance) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (BlockChangeQueueElement queueElement : queueElements) {
                pstmt.setInt(1, queueElement.blockID);
                pstmt.setInt(2, queueElement.metadata);
                pstmt.setInt(3, queueElement.pickBlockID);
                pstmt.setInt(4, queueElement.pickBlockMeta);
                pstmt.setString(5, queueElement.stackTrace);
                pstmt.setString(6, queueElement.encodedNBT);
                pstmt.setInt(7, (int) Math.round(queueElement.x));
                pstmt.setInt(8, (int) Math.round(queueElement.y));
                pstmt.setInt(9, (int) Math.round(queueElement.z));
                pstmt.setInt(10, queueElement.dimensionId);
                pstmt.setTimestamp(11, new Timestamp(queueElement.timestamp));
                pstmt.setString(12, queueElement.closestPlayerUUID);
                pstmt.setDouble(13, queueElement.closestPlayerDistance);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.encodedNBT = resultSet.getString("encodedNBT");
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
        if (!globalBlockChangeLogging && !RegionRegistry.containsBlock(dimensionId, x, y, z)) {
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

        if (logNBT) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tileEntity.writeToNBT(tag);
                queueElement.encodedNBT = NBTConverter.encodeToString(tag);
            }
        }

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
