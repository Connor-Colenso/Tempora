package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.rendering.RenderUtils.getRenderAlpha;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.nbt.NBTConverter.NO_NBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.colen.tempora.utils.nbt.NBTConverter;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement> {

    private boolean logNBT;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerBlockBreakLogger;
    }

    @Override
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        // Map to store the latest event at each position
        Map<String, GenericQueueElement> latestEventsByPos = new HashMap<>();

        for (GenericQueueElement element : eventsToRenderInWorld) {
            if (element.dimensionId != playerDim) continue;

            // Unique key per block position and dimension
            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;

            // Only keep the most recent event for that position
            GenericQueueElement existing = latestEventsByPos.get(key);
            if (existing == null || element.timestamp > existing.timestamp) {
                latestEventsByPos.put(key, element);
            }
        }

        List<GenericQueueElement> sortedList = RenderUtils.getSortedElementsByDistance(latestEventsByPos, e);

        // Now render only the latest event at each block position
        for (GenericQueueElement element : sortedList) {
            if (element instanceof PlayerBlockBreakQueueElement pbbe) {

                NBTTagCompound nbt = null;
                if (!Objects.equals(pbbe.encodedNBT, NO_NBT)) {
                    nbt = NBTConverter.decodeFromString(pbbe.encodedNBT);
                }

                RenderUtils.renderBlockInWorld(e, element.x, element.y, element.z, pbbe.blockID, pbbe.metadata, getRenderAlpha(element), nbt);
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
        logNBT = config.getBoolean("logNBT", getSQLTableName(), true, """
            If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
            WARNING: NBT may be large and this could cause the database to grow much quicker.
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
    public void threadedSaveEvents(List<PlayerBlockBreakQueueElement> elements) throws SQLException {
        if (elements == null || elements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, blockId, metadata, pickBlockID, pickBlockMeta, encodedNBT, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerBlockBreakQueueElement elem : elements) {
                pstmt.setString(1, elem.playerUUIDWhoBrokeBlock);
                pstmt.setInt(2, elem.blockID);
                pstmt.setInt(3, elem.metadata);
                pstmt.setInt(4, elem.pickBlockID);
                pstmt.setInt(5, elem.pickBlockMeta);
                pstmt.setString(6, elem.encodedNBT);
                pstmt.setDouble(7, elem.x);
                pstmt.setDouble(8, elem.y);
                pstmt.setDouble(9, elem.z);
                pstmt.setInt(10, elem.dimensionId);
                pstmt.setTimestamp(11, new Timestamp(elem.timestamp));
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

        if (logNBT) {
            TileEntity tileEntity = event.world.getTileEntity(event.x, event.y, event.z);
            if (tileEntity != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tileEntity.writeToNBT(tag);
                queueElement.encodedNBT = NBTConverter.encodeToString(tag);
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
}
