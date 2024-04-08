package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.QueueElement.BlockPlaceQueueElement;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockPlaceLogger extends GenericPositionalLogger<BlockPlaceQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s placed [%s:%d] at [%s, %s, %s] on %s",
            rs.getString("playerName"),
            Block.getBlockById(rs.getInt("blockId")).getUnlocalizedName(), // Method to get the block name from ID
            rs.getInt("metadata"),
            rs.getInt("x"),
            rs.getInt("y"),
            rs.getInt("z"),
            rs.getTimestamp("timestamp"));
    }

    @Override
    public void initTable() {
        try {
            positionLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTableName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "playerName TEXT NOT NULL,"
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
                + "(playerName, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, blockPlaceQueueElement.playerWhoPlacedBlock);
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

        BlockPlaceQueueElement queueElement = new BlockPlaceQueueElement(
            event.x,
            event.y,
            event.z,
            event.world.provider.dimensionId);
        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        if (event.player instanceof EntityPlayerMP) {
            queueElement.playerWhoPlacedBlock = event.player
                .getDisplayName();
        } else {
            queueElement.playerWhoPlacedBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        eventQueue.add(queueElement);
    }
}
