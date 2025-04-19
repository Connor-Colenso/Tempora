package com.colen.tempora.logging.loggers.block_change_logger;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import net.minecraft.block.Block;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    @Override
    public void initTable() {
        try {
            positionalLoggerDBConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + getLoggerName() + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "blockId INTEGER NOT NULL," +
                    "metadata INTEGER NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "dimensionID INTEGER NOT NULL," +
                    "timestamp DATETIME NOT NULL);"
            ).execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(BlockChangeQueueElement e) {
        try {
            final String sql = "INSERT INTO " + getLoggerName() +
                "(blockId, metadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
            pstmt.setInt(1, e.blockID);
            pstmt.setInt(2, e.metadata);
            pstmt.setInt(3, (int) Math.round(e.x));
            pstmt.setInt(4, (int) Math.round(e.y));
            pstmt.setInt(5, (int) Math.round(e.z));
            pstmt.setInt(6, e.dimensionId);
            pstmt.setTimestamp(7, new Timestamp(e.timestamp));
            pstmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.x = resultSet.getInt("x");
            queueElement.y = resultSet.getInt("y");
            queueElement.z = resultSet.getInt("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");
            events.add(queueElement);
        }
        return events;
    }

    public void recordSetBlock(int x, int y, int z, Block blockIn, int metadataIn, int dimensionId) {
        BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = dimensionId;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.blockID = Block.getIdFromBlock(blockIn);
        queueElement.metadata = metadataIn;

        queueEvent(queueElement);
    }
}
