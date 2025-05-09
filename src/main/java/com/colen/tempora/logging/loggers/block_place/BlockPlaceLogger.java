package com.colen.tempora.logging.loggers.block_place;

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
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockPlaceLogger extends GenericPositionalLogger<BlockPlaceQueueElement> {

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL"));
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            BlockPlaceQueueElement queueElement = new BlockPlaceQueueElement();
            queueElement.x = resultSet.getInt("x");
            queueElement.y = resultSet.getInt("y");
            queueElement.z = resultSet.getInt("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.playerNameWhoPlacedBlock = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvent(BlockPlaceQueueElement blockPlaceQueueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName()
            + "(playerUUID, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
        final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
        pstmt.setString(1, blockPlaceQueueElement.playerNameWhoPlacedBlock);
        pstmt.setInt(2, blockPlaceQueueElement.blockID);
        pstmt.setInt(3, blockPlaceQueueElement.metadata);
        pstmt.setDouble(4, blockPlaceQueueElement.x);
        pstmt.setDouble(5, blockPlaceQueueElement.y);
        pstmt.setDouble(6, blockPlaceQueueElement.z);
        pstmt.setInt(7, blockPlaceQueueElement.dimensionId);
        pstmt.setTimestamp(8, new Timestamp(blockPlaceQueueElement.timestamp));
        pstmt.executeUpdate();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockPlace(final @NotNull PlaceEvent event) {
        if (isClientSide()) return; // Server side only

        BlockPlaceQueueElement queueElement = new BlockPlaceQueueElement();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionId = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        if (event.player instanceof EntityPlayerMP) {
            queueElement.playerNameWhoPlacedBlock = event.player.getUniqueID()
                .toString();
        } else {
            queueElement.playerNameWhoPlacedBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }
}
