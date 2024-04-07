package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.QueueElement.BlockBreakQueueElement;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakLogger extends GenericLoggerPositional<BlockBreakQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return "";
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
                + "(playerName, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, blockBreakQueueElement.playerWhoBrokeBlock);
            pstmt.setInt(2, blockBreakQueueElement.metadata);
            pstmt.setDouble(3, blockBreakQueueElement.x);
            pstmt.setDouble(4, blockBreakQueueElement.y);
            pstmt.setDouble(5, blockBreakQueueElement.z);
            pstmt.setInt(6, blockBreakQueueElement.dimensionId);
            pstmt.setTimestamp(7, new Timestamp(blockBreakQueueElement.timestamp));
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

        BlockBreakQueueElement queueElement = new BlockBreakQueueElement(
            event.x,
            event.y,
            event.z,
            event.world.provider.dimensionId);
        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerWhoBrokeBlock = event.getPlayer()
                .getDisplayName();
        } else {
            queueElement.playerWhoBrokeBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        eventQueue.add(queueElement);
    }
}
