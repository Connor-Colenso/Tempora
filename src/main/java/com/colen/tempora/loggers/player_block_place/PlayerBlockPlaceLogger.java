package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.RenderingUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerBlockPlaceLogger extends GenericPositionalLogger<PlayerBlockPlaceQueueElement> {

    private boolean logNBT;

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            true,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerBlockPlaceLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        List<PlayerBlockPlaceQueueElement> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            e);

        for (PlayerBlockPlaceQueueElement pbbqe : sortedList) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                e,
                pbbqe,
                pbbqe.blockID,
                pbbqe.metadata,
                pbbqe.encodedNBT,
                pbbqe.playerUUID,
                getLoggerType());
        }

        for (PlayerBlockPlaceQueueElement pbbqe : nonTransparentEventsToRenderInWorld) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                e,
                pbbqe,
                pbbqe.blockID,
                pbbqe.metadata,
                pbbqe.encodedNBT,
                pbbqe.playerUUID,
                getLoggerType());
        }
    }

    @Override
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            PlayerBlockPlaceQueueElement queueElement = new PlayerBlockPlaceQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.playerUUID = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
            queueElement.encodedNBT = resultSet.getString("encodedNBT");
            queueElement.blockID = resultSet.getInt("blockID");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.pickBlockID = resultSet.getInt("pickBlockID");
            queueElement.pickBlockMeta = resultSet.getInt("pickBlockMeta");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<PlayerBlockPlaceQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getLoggerName()
            + " (playerUUID, encodedNBT, blockId, metadata, pickBlockId, pickBlockMeta, eventID, x, y, z, dimensionID, timestamp, versionID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = databaseManager.getDBConn()
            .prepareStatement(sql)) {
            for (PlayerBlockPlaceQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setString(index++, queueElement.playerUUID);
                pstmt.setString(index++, queueElement.encodedNBT);
                pstmt.setInt(index++, queueElement.blockID);
                pstmt.setInt(index++, queueElement.metadata);
                pstmt.setInt(index++, queueElement.pickBlockID);
                pstmt.setInt(index++, queueElement.pickBlockMeta);
                DatabaseUtils.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockPlace(final @NotNull PlaceEvent event) {
        if (isClientSide()) return; // Server side only
        if (event.isCanceled()) return;

        PlayerBlockPlaceQueueElement queueElement = new PlayerBlockPlaceQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionID = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.world.getBlockMetadata(event.x, event.y, event.z);

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

        // Log NBT.
        queueElement.encodedNBT = getEncodedTileEntityNBT(event.world, event.x, event.y, event.z, logNBT);

        if (event.player instanceof EntityPlayerMP) {
            queueElement.playerUUID = event.player.getUniqueID()
                .toString();
        } else {
            queueElement.playerUUID = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }

}
