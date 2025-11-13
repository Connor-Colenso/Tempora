package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.nbt.NBTConverter.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTConverter.NO_NBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.EventLoggingHelper;
import com.colen.tempora.utils.nbt.NBTConverter;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement>
    implements ISupportsUndo {

    private static boolean logNBT;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerBlockBreakLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        List<GenericQueueElement> sortedList = RenderUtils.getSortedLatestEventsByDistance(eventsToRenderInWorld, e);

        for (GenericQueueElement element : sortedList) {
            if (element instanceof PlayerBlockBreakQueueElement pbbe) {
                try {
                    NBTTagCompound nbt = null;
                    if (!Objects.equals(pbbe.encodedNBT, NO_NBT) && !Objects.equals(pbbe.encodedNBT, NBT_DISABLED)) {
                        nbt = NBTConverter.decodeFromString(pbbe.encodedNBT);
                    }

                    RenderUtils.renderBlockInWorld(
                        e,
                        element.x,
                        element.y,
                        element.z,
                        pbbe.blockID,
                        pbbe.metadata,
                        nbt,
                        getLoggerType());
                } catch (Exception exception) {
                    // Render an error block here instead, if something went critically wrong.
                    FMLLog.warning(
                        "[Tempora] Failed to render %s event (eventID=%s) at (%.1f, %.1f, %.1f) in dim %d. "
                            + "BlockID=%d:%d PickBlock=%d:%d Player=%s NBT=%s Timestamp=%d | Exception: %s: %s",
                        getLoggerType(), // LoggerEnum
                        pbbe.eventID, // Unique ID
                        pbbe.x,
                        pbbe.y,
                        pbbe.z, // Coordinates
                        pbbe.dimensionId, // Dimension ID
                        pbbe.blockID,
                        pbbe.metadata, // Block ID + metadata
                        pbbe.pickBlockID,
                        pbbe.pickBlockMeta, // Pick block (if any)
                        pbbe.playerUUIDWhoBrokeBlock, // Player UUID
                        (pbbe.encodedNBT != null && !pbbe.encodedNBT.isEmpty()
                            ? pbbe.encodedNBT.substring(0, Math.min(pbbe.encodedNBT.length(), 64)) + "..."
                            : "none"), // Safe truncated NBT preview
                        pbbe.timestamp, // Timestamp
                        exception.getClass()
                            .getSimpleName(), // Exception type
                        exception.getMessage() // Exception message
                    );

                    // Optionally print full stack trace to console for devs
                    exception.printStackTrace();

                    RenderUtils.renderBlockInWorld(
                        e,
                        pbbe.x,
                        pbbe.y,
                        pbbe.z,
                        Block.getIdFromBlock(Tempora.renderingErrorBlock),
                        0,
                        null,
                        getLoggerType());
                }
            }
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("encodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),
            new ColumnDef("metadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getSQLTableName(),
            true,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this will cause the database to grow much quicker.
                """);
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {

        try {
            ArrayList<GenericQueueElement> eventList = new ArrayList<>();

            while (resultSet.next()) {

                PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
                queueElement.x = resultSet.getInt("x");
                queueElement.y = resultSet.getInt("y");
                queueElement.z = resultSet.getInt("z");
                queueElement.dimensionId = resultSet.getInt("dimensionID");
                queueElement.timestamp = resultSet.getLong("timestamp");

                queueElement.encodedNBT = resultSet.getString("encodedNBT");
                queueElement.playerUUIDWhoBrokeBlock = resultSet.getString("playerUUID");
                queueElement.blockID = resultSet.getInt("blockId");
                queueElement.metadata = resultSet.getInt("metadata");
                queueElement.pickBlockID = resultSet.getInt("pickBlockID");
                queueElement.pickBlockMeta = resultSet.getInt("pickBlockMeta");

                eventList.add(queueElement);
            }

            return eventList;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void threadedSaveEvents(List<PlayerBlockBreakQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, blockId, metadata, pickBlockID, pickBlockMeta, encodedNBT, eventID, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerBlockBreakQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setString(index++, queueElement.playerUUIDWhoBrokeBlock);
                pstmt.setInt(index++, queueElement.blockID);
                pstmt.setInt(index++, queueElement.metadata);
                pstmt.setInt(index++, queueElement.pickBlockID);
                pstmt.setInt(index++, queueElement.pickBlockMeta);
                pstmt.setString(index++, queueElement.encodedNBT);
                EventLoggingHelper.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionId = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        TileEntity tileEntity = event.world.getTileEntity(event.x, event.y, event.z);
        if (tileEntity != null) {
            if (logNBT) {
                NBTTagCompound tag = new NBTTagCompound();
                tileEntity.writeToNBT(tag);
                queueElement.encodedNBT = NBTConverter.encodeToString(tag);
            } else {
                queueElement.encodedNBT = NBTConverter.NBT_DISABLED;
            }
        } else {
            queueElement.encodedNBT = NO_NBT;
        }

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = getPickBlockSafe(event.block, event.world, event.x, event.y, event.z);
        if (pickStack != null && pickStack.getItem() != null) {
            queueElement.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            queueElement.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            queueElement.pickBlockID = queueElement.blockID;
            queueElement.pickBlockMeta = queueElement.metadata;
        }

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerUUIDWhoBrokeBlock = event.getPlayer()
                .getUniqueID()
                .toString();
        } else {
            queueElement.playerUUIDWhoBrokeBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }

    // Todo return enum determining why fail occurred?
    @Override
    public boolean undoEvent(String eventUUID) {

        PlayerBlockBreakQueueElement queueElement = queryEventByEventID(eventUUID);

        // NBT existed but was not logged, it is not safe to undo this event.
        if (queueElement.encodedNBT.equals(NBT_DISABLED)) return false;

        World w = MinecraftServer.getServer()
            .worldServerForDimension(queueElement.dimensionId);

        Block block = Block.getBlockById(queueElement.blockID);
        if (block == null) return false;
        w.setBlock((int) queueElement.x, (int) queueElement.y, (int) queueElement.z, block, queueElement.metadata, 2);

        // Block had no NBT.
        if (queueElement.encodedNBT.equals(NO_NBT)) return true;

        try {
            TileEntity tileEntity = TileEntity
                .createAndLoadEntity(NBTConverter.decodeFromString(queueElement.encodedNBT));
            w.setTileEntity((int) queueElement.x, (int) queueElement.y, (int) queueElement.z, tileEntity);
        } catch (Exception e) {
            // todo Improve this
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
