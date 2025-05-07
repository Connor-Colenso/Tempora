package com.colen.tempora.logging.loggers.block_change_logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.GenericUtils;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> {

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("blockId", "INTEGER", "NOT NULL"),
            new ColumnDef("metadata", "INTEGER", "NOT NULL"),
            new ColumnDef("stackTrace", "TEXT", "NOT NULL"));
    }

    @Override
    public void threadedSaveEvent(BlockChangeQueueElement queueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName()
            + " (blockId, metadata, stackTrace, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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
