package com.colen.tempora.logging.loggers.block_change_logger;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import com.colen.tempora.utils.GenericUtils;
import cpw.mods.fml.common.FMLContainer;
import cpw.mods.fml.common.Loader;
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
                    "stackTrace TEXT," +
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
    public void threadedSaveEvent(BlockChangeQueueElement queueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName() +
            " (blockId, metadata, stackTrace, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
        pstmt.setInt(1, queueElement.blockID);
        pstmt.setInt(2, queueElement.metadata);
        pstmt.setString(3, queueElement.stackTrace);
        pstmt.setInt(4, (int) Math.round(queueElement.x));
        pstmt.setInt(5, (int) Math.round(queueElement.y));
        pstmt.setInt(6, (int) Math.round(queueElement.z));
        pstmt.setInt(7, queueElement.dimensionId);
        pstmt.setTimestamp(8, new Timestamp(queueElement.timestamp));
        pstmt.executeUpdate();
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.blockID = resultSet.getInt("blockId");
            queueElement.metadata = resultSet.getInt("metadata");
            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.x = resultSet.getInt("x");
            queueElement.y = resultSet.getInt("y");
            queueElement.z = resultSet.getInt("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");
            events.add(queueElement);
        }
        return events;
    }

    public void recordSetBlock(int x, int y, int z, Block blockIn, int metadataIn, int dimensionId, String modID) {
        BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = dimensionId;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.stackTrace = modID + " : " + GenericUtils.getCallingClassChain();
        queueElement.blockID = Block.getIdFromBlock(blockIn);
        queueElement.metadata = metadataIn;

        queueEvent(queueElement);
    }
}
