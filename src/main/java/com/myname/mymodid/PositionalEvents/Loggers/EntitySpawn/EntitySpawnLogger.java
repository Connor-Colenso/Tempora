package com.myname.mymodid.PositionalEvents.Loggers.EntitySpawn;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnQueueElement> {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntitySpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return;
        if (event.isCanceled()) return;

        EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement();
        queueElement.x = event.entityLiving.posX;
        queueElement.y = event.entityLiving.posY;
        queueElement.z = event.entityLiving.posZ;
        queueElement.dimensionId = event.entityLiving.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.entityName = event.entityLiving.getCommandSenderName();

        eventQueue.add(queueElement);
    }

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement();
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
    public void threadedSaveEvent(EntitySpawnQueueElement entitySpawnQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(entityName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, entitySpawnQueueElement.entityName);
            pstmt.setDouble(2, entitySpawnQueueElement.x);
            pstmt.setDouble(3, entitySpawnQueueElement.y);
            pstmt.setDouble(4, entitySpawnQueueElement.z);
            pstmt.setInt(5, entitySpawnQueueElement.dimensionId);
            pstmt.setTimestamp(6, new Timestamp(entitySpawnQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
