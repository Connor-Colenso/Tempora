package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;
import com.myname.mymodid.TemporaUtils;
import com.myname.mymodid.Utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakLogger extends GenericPositionalLogger<BlockBreakQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {

        try {
            ArrayList<ISerializable> eventList = new ArrayList<>();

            while (resultSet.next()) {

                BlockBreakQueueElement queueElement = new BlockBreakQueueElement();
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
    public void initTable() {
        try {
            positionLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTableName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "playerUUID TEXT NOT NULL,"
                        + "metadata INTEGER NOT NULL,"
                        + "blockId INTEGER NOT NULL,"
                        + "x REAL NOT NULL,"
                        + "y REAL NOT NULL,"
                        + "z REAL NOT NULL,"
                        + "dimensionID INTEGER DEFAULT 0 NOT NULL,"
                        + "timestamp DATETIME NOT NULL);")
                .execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(BlockBreakQueueElement blockBreakQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(playerUUID, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, blockBreakQueueElement.playerNameWhoBrokeBlock);
            pstmt.setInt(2, blockBreakQueueElement.blockID);
            pstmt.setInt(3, blockBreakQueueElement.metadata);
            pstmt.setDouble(4, blockBreakQueueElement.x);
            pstmt.setDouble(5, blockBreakQueueElement.y);
            pstmt.setDouble(6, blockBreakQueueElement.z);
            pstmt.setInt(7, blockBreakQueueElement.dimensionId);
            pstmt.setTimestamp(8, new Timestamp(blockBreakQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (isClientSide()) return;

        BlockBreakQueueElement queueElement = new BlockBreakQueueElement();
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

        eventQueue.add(queueElement);
    }
}
