package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakLogger extends GenericPositionalLogger<BlockBreakQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected IMessage generatePacket(ResultSet resultSet) throws SQLException {

try {
    ArrayList<BlockBreakQueueElement> eventList = new ArrayList<>();
    int counter = 0;

    while (resultSet.next() && counter < MAX_DATA_ROWS_PER_PACKET) {
        int x = resultSet.getInt("x");
        int y = resultSet.getInt("y");
        int z = resultSet.getInt("z");

        BlockBreakQueueElement queueElement = new BlockBreakQueueElement(x, y, z, 0);
        queueElement.playerUUIDWhoBrokeBlock = resultSet.getString("playerName");
        queueElement.blockID = resultSet.getInt("blockId");
        queueElement.metadata = resultSet.getInt("metadata");
        queueElement.timestamp = resultSet.getLong("timestamp");

        counter++;
    }

    BlockBreakPacketHandler packet = new BlockBreakPacketHandler();
    packet.eventList = eventList;

    return packet;
    }
    catch (Exception e) {
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
    public void threadedSaveEvent(BlockBreakQueueElement blockBreakQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(playerName, blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, blockBreakQueueElement.playerUUIDWhoBrokeBlock);
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

        BlockBreakQueueElement queueElement = new BlockBreakQueueElement(
            event.x,
            event.y,
            event.z,
            event.world.provider.dimensionId);
        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerUUIDWhoBrokeBlock = event.getPlayer().getUniqueID().toString();
        } else {
            queueElement.playerUUIDWhoBrokeBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        eventQueue.add(queueElement);
    }
}
