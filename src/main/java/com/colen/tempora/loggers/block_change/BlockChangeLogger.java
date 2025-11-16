package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.TemporaUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.EventLoggingHelper;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.RenderingUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    private boolean globalBlockChangeLogging;

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.BlockChangeLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        List<BlockChangeQueueElement> sortedList = getSortedLatestEventsByDistance(eventsToRenderInWorld, e);

        for (BlockChangeQueueElement bcqe : sortedList) {
            RenderingUtils.renderBlockWithLogging(
                e,
                bcqe,
                bcqe.beforeBlockID,
                bcqe.beforeMetadata,
                bcqe.beforeEncodedNBT,
                bcqe.closestPlayerUUID,
                getLoggerType());
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("beforeBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("afterBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("stackTrace", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getSQLTableName(), false, """
            If true, overrides all custom regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);

        logNBT = config.getBoolean(
            "logNBT",
            getSQLTableName(),
            false,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    public void threadedSaveEvents(List<BlockChangeQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (beforeBlockID, beforeMetadata, beforePickBlockID, beforePickBlockMeta, beforeEncodedNBT, afterBlockID, afterMetadata, afterPickBlockID, afterPickBlockMeta, afterEncodedNBT, stackTrace, closestPlayerUUID, closestPlayerDistance, eventID, x, y, z, dimensionID, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (BlockChangeQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setInt(index++, queueElement.beforeBlockID);
                pstmt.setInt(index++, queueElement.beforeMetadata);
                pstmt.setInt(index++, queueElement.beforePickBlockID);
                pstmt.setInt(index++, queueElement.beforePickBlockMeta);
                pstmt.setString(index++, queueElement.beforeEncodedNBT);

                pstmt.setInt(index++, queueElement.afterBlockID);
                pstmt.setInt(index++, queueElement.afterMetadata);
                pstmt.setInt(index++, queueElement.afterPickBlockID);
                pstmt.setInt(index++, queueElement.afterPickBlockMeta);
                pstmt.setString(index++, queueElement.afterEncodedNBT);

                pstmt.setString(index++, queueElement.stackTrace);
                pstmt.setString(index++, queueElement.closestPlayerUUID);
                pstmt.setDouble(index++, queueElement.closestPlayerDistance);

                EventLoggingHelper.defaultColumnEntries(queueElement, pstmt, index);

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
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.beforeBlockID = resultSet.getInt("beforeBlockID");
            queueElement.beforeMetadata = resultSet.getInt("beforeMetadata");
            queueElement.beforePickBlockID = resultSet.getInt("beforePickBlockID");
            queueElement.beforePickBlockMeta = resultSet.getInt("beforePickBlockMeta");
            queueElement.beforeEncodedNBT = resultSet.getString("beforeEncodedNBT");

            queueElement.afterBlockID = resultSet.getInt("afterBlockID");
            queueElement.afterMetadata = resultSet.getInt("afterMetadata");
            queueElement.afterPickBlockID = resultSet.getInt("afterPickBlockID");
            queueElement.afterPickBlockMeta = resultSet.getInt("afterPickBlockMeta");
            queueElement.afterEncodedNBT = resultSet.getString("afterEncodedNBT");

            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.closestPlayerUUID = PlayerUtils.UUIDToName(resultSet.getString("closestPlayerUUID"));
            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");

            events.add(queueElement);
        }
        return events;
    }

    public void recordSetBlock(int x, int y, int z, SetBlockEventInfo setBlockEventInfo, WorldProvider worldProvider,
        String modID) {

        // Only log changes if (x, y, z) is inside a defined region
        if (!globalBlockChangeLogging && !RegionRegistry.containsBlock(worldProvider.dimensionId, x, y, z)) {
            return;
        }

        if (!isChunkPopulatedAt(worldProvider.worldObj, x, z)) return;

        final BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = worldProvider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.stackTrace = modID + " : " + GenericUtils.getCallingClassChain();

        queueElement.beforeBlockID = setBlockEventInfo.beforeBlockID;
        queueElement.beforeMetadata = setBlockEventInfo.beforeMeta;
        queueElement.beforePickBlockID = setBlockEventInfo.beforePickBlockID;
        queueElement.beforePickBlockMeta = setBlockEventInfo.beforePickBlockMeta;
        queueElement.beforeEncodedNBT = setBlockEventInfo.beforeEncodedNBT;

        queueElement.afterBlockID = setBlockEventInfo.afterBlockID;
        queueElement.afterMetadata = setBlockEventInfo.afterMeta;
        queueElement.afterPickBlockID = setBlockEventInfo.afterPickBlockID;
        queueElement.afterPickBlockMeta = setBlockEventInfo.afterPickBlockMeta;
        queueElement.afterEncodedNBT = setBlockEventInfo.afterEncodedNBT;

        EntityPlayer closestPlayer = worldProvider.worldObj.getClosestPlayer(x, y, z, -1);;
        double closestDistance = -1;

        if (closestPlayer != null) closestDistance = closestPlayer.getDistance(x, y, z);

        queueElement.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

    private boolean isChunkPopulatedAt(World world, int blockX, int blockZ) {
        // Convert block coordinates to chunk coordinates
        int chunkX = blockX >> 4; // divide by 16
        int chunkZ = blockZ >> 4;

        // Get the chunk object
        Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);

        return chunk.isTerrainPopulated;
    }
}
