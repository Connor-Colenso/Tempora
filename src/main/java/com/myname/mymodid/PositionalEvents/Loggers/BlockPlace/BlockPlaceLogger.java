package com.myname.mymodid.PositionalEvents.Loggers.BlockPlace;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;
import com.myname.mymodid.Utils.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockPlaceLogger extends GenericPositionalLogger<BlockPlaceQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

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
    public void threadedSaveEvent(BlockPlaceQueueElement blockPlaceQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(playerUUID, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, blockPlaceQueueElement.playerNameWhoPlacedBlock);
            pstmt.setInt(2, blockPlaceQueueElement.blockID);
            pstmt.setInt(3, blockPlaceQueueElement.metadata);
            pstmt.setDouble(4, blockPlaceQueueElement.x);
            pstmt.setDouble(5, blockPlaceQueueElement.y);
            pstmt.setDouble(6, blockPlaceQueueElement.z);
            pstmt.setInt(7, blockPlaceQueueElement.dimensionId);
            pstmt.setTimestamp(8, new Timestamp(blockPlaceQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
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

        eventQueue.add(queueElement);
    }
}
