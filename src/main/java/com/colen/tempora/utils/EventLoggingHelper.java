package com.colen.tempora.utils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.colen.tempora.loggers.generic.GenericQueueElement;

public class EventLoggingHelper {

    public static void defaultColumnEntries(GenericQueueElement queueElement, PreparedStatement pstmt, int index)
        throws SQLException {
        pstmt.setString(index++, queueElement.eventID);
        pstmt.setDouble(index++, queueElement.x);
        pstmt.setDouble(index++, queueElement.y);
        pstmt.setDouble(index++, queueElement.z);
        pstmt.setInt(index++, queueElement.dimensionId);
        pstmt.setTimestamp(index, new Timestamp(queueElement.timestamp));
    }
}
