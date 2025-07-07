package com.colen.tempora.logging.loggers.player_block_break;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement> {

    @Override
    public String getSQLTableName() {
        return "PlayerBlockBreakLogger";
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("metadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL DEFEAULT -1"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"));
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
            + " (playerUUID, blockId, metadata, pickBlockID, pickBlockMeta, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerBlockBreakQueueElement elem : elements) {
                pstmt.setString(1, elem.playerUUIDWhoBrokeBlock);
                pstmt.setInt(2, elem.blockID);
                pstmt.setInt(3, elem.metadata);
                pstmt.setInt(4, elem.pickBlockID);
                pstmt.setInt(5, elem.pickBlockMeta);
                pstmt.setDouble(6, elem.x);
                pstmt.setDouble(7, elem.y);
                pstmt.setDouble(8, elem.z);
                pstmt.setInt(9, elem.dimensionId);
                pstmt.setTimestamp(10, new Timestamp(elem.timestamp));
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
