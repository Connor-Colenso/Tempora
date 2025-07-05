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

import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.ISerializable;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnQueueElement> {

    @Override
    public String getSQLTableName() {
        return "EntitySpawnLogger";
    }

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
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {
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
    public void threadedSaveEvents(List<EntitySpawnQueueElement> entitySpawnQueueElements) throws SQLException {
        if (entitySpawnQueueElements == null || entitySpawnQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntitySpawnQueueElement element : entitySpawnQueueElements) {
                pstmt.setString(1, element.entityName);
                pstmt.setDouble(2, element.x);
                pstmt.setDouble(3, element.y);
                pstmt.setDouble(4, element.z);
                pstmt.setInt(5, element.dimensionId);
                pstmt.setTimestamp(6, new Timestamp(element.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }
}
