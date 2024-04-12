package com.myname.mymodid.PositionalEvents.Loggers.EntityPosition;

import static com.myname.mymodid.Config.loggingIntervals;
import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionQueueElement> {

    public static int entityMovementLoggingInterval;

    @Override
    public void handleConfig(Configuration config) {
        entityMovementLoggingInterval = config.getInt(
            "playerMovementLoggingInterval",
            loggingIntervals,
            500,
            1,
            Integer.MAX_VALUE,
            "How often entities location is recorded to the database. Measured in ticks (20/second).");
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            EntityPositionQueueElement queueElement = new EntityPositionQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.entityName = resultSet.getString("entityName");
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
                        + "entityName TEXT NOT NULL,"
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
    public void threadedSaveEvent(EntityPositionQueueElement entityPositionQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(entityName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, entityPositionQueueElement.entityName);
            pstmt.setDouble(2, entityPositionQueueElement.x);
            pstmt.setDouble(3, entityPositionQueueElement.y);
            pstmt.setDouble(4, entityPositionQueueElement.z);
            pstmt.setInt(5, entityPositionQueueElement.dimensionId);
            pstmt.setTimestamp(6, new Timestamp(entityPositionQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entityLiving.ticksExisted % entityMovementLoggingInterval != 0) return; // As an example, track every
                                                                                          // 20 seconds.
        if (event.entityLiving instanceof EntityPlayerMP) return; // Do not track players here, we do this elsewhere.

        EntityPositionQueueElement queueElement = new EntityPositionQueueElement();
        queueElement.x = event.entityLiving.posX;
        queueElement.y = event.entityLiving.posY;
        queueElement.z = event.entityLiving.posZ;
        queueElement.dimensionId = event.entityLiving.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.entityName = event.entityLiving.getCommandSenderName();

        eventQueue.add(queueElement);
    }

}
