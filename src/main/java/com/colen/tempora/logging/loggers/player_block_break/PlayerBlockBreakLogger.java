package com.colen.tempora.logging.loggers.player_block_break;

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
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement> {

    @Override
    public String getSQLTableName() {
        return "PlayerBlockBreakLogger";
    }

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL"));
    }

    @Override
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {

        try {
            ArrayList<ISerializable> eventList = new ArrayList<>();

            while (resultSet.next()) {

                PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
                queueElement.x = resultSet.getInt("x");
                queueElement.y = resultSet.getInt("y");
                queueElement.z = resultSet.getInt("z");
                queueElement.dimensionId = resultSet.getInt("dimensionID");
                queueElement.timestamp = resultSet.getLong("timestamp");

                queueElement.playerNameWhoBrokeBlock = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
                queueElement.blockID = resultSet.getInt("blockId");
                queueElement.metadata = resultSet.getInt("metadata");

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
            + " (playerUUID, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql)) {
            for (PlayerBlockBreakQueueElement elem : elements) {
                pstmt.setString(1, elem.playerNameWhoBrokeBlock);
                pstmt.setInt(2, elem.blockID);
                pstmt.setInt(3, elem.metadata);
                pstmt.setDouble(4, elem.x);
                pstmt.setDouble(5, elem.y);
                pstmt.setDouble(6, elem.z);
                pstmt.setInt(7, elem.dimensionId);
                pstmt.setTimestamp(8, new Timestamp(elem.timestamp));
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

        PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionId = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerNameWhoBrokeBlock = event.getPlayer()
                .getUniqueID()
                .toString();
        } else {
            queueElement.playerNameWhoBrokeBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }
}
