package com.colen.tempora.logging.loggers.player_block_place;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockPlaceLogger extends GenericPositionalLogger<PlayerBlockPlaceQueueElement> {

    @Override
    public String getSQLTableName() {
        return "PlayerBlockPlaceLogger";
    }

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL"));
    }

    @Override
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            PlayerBlockPlaceQueueElement queueElement = new PlayerBlockPlaceQueueElement();
            queueElement.x = resultSet.getInt("x");
            queueElement.y = resultSet.getInt("y");
            queueElement.z = resultSet.getInt("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.playerNameWhoPlacedBlock = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.pickBlockID = resultSet.getInt("pickBlockId");
            queueElement.pickBlockMeta = resultSet.getInt("pickBlockMeta");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<PlayerBlockPlaceQueueElement> blockPlaceQueueElements) throws SQLException {
        if (blockPlaceQueueElements == null || blockPlaceQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, blockId, metadata, pickBlockId, pickBlockMeta, x, y, z, dimensionID, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerBlockPlaceQueueElement element : blockPlaceQueueElements) {
                pstmt.setString(1, element.playerNameWhoPlacedBlock);
                pstmt.setInt(2, element.blockID);
                pstmt.setInt(3, element.metadata);
                pstmt.setInt(4, element.pickBlockID);
                pstmt.setInt(5, element.pickBlockMeta);
                pstmt.setDouble(6, element.x);
                pstmt.setDouble(7, element.y);
                pstmt.setDouble(8, element.z);
                pstmt.setInt(9, element.dimensionId);
                pstmt.setTimestamp(10, new Timestamp(element.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockPlace(final @NotNull PlaceEvent event) {
        if (isClientSide()) return; // Server side only

        PlayerBlockPlaceQueueElement queueElement = new PlayerBlockPlaceQueueElement();

        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionId = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.world.getBlockMetadata(event.x, event.y, event.z);

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = event.block.getPickBlock(null, event.world, event.x, event.y, event.z);
        if (pickStack != null && pickStack.getItem() != null) {
            queueElement.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            queueElement.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            queueElement.pickBlockID = queueElement.blockID;
            queueElement.pickBlockMeta = queueElement.metadata;
        }

        if (event.player instanceof EntityPlayerMP) {
            queueElement.playerNameWhoPlacedBlock = event.player.getUniqueID().toString();
        } else {
            queueElement.playerNameWhoPlacedBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }

}
