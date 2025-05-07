package com.colen.tempora.logging.loggers.entity_spawn;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnQueueElement> {

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(new ColumnDef("entityName", "TEXT", "NOT NULL"));
    }

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

        queueEvent(queueElement);
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
    public void threadedSaveEvent(EntitySpawnQueueElement entitySpawnQueueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName()
            + "(entityName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
        final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
        pstmt.setString(1, entitySpawnQueueElement.entityName);
        pstmt.setDouble(2, entitySpawnQueueElement.x);
        pstmt.setDouble(3, entitySpawnQueueElement.y);
        pstmt.setDouble(4, entitySpawnQueueElement.z);
        pstmt.setInt(5, entitySpawnQueueElement.dimensionId);
        pstmt.setTimestamp(6, new Timestamp(entitySpawnQueueElement.timestamp));
        pstmt.executeUpdate();
    }
}
