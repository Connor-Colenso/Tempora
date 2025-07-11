package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.mixin_interfaces.IEntityMixin;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntitySpawnLogger;
    }

    @Override
    public void renderEventInWorld(RenderWorldLastEvent e) {

    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("entityName", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("entityUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entity instanceof EntityPlayerMP) return;

        IEntityMixin entityMixin = (IEntityMixin) event.entity;
        if (entityMixin.getTempora$HasBeenLogged()) return;

        // Mark as logged, this is persistent with nbt.
        entityMixin.setTempora$HasBeenLogged(true);

        EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement();
        queueElement.x = event.entity.posX;
        queueElement.y = event.entity.posY;
        queueElement.z = event.entity.posZ;
        queueElement.dimensionId = event.entity.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.entityName = event.entity.getCommandSenderName();
        queueElement.entityUUID = event.entity.getUniqueID()
            .toString();

        queueEvent(queueElement);
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement();
            queueElement.entityName = resultSet.getString("entityName");
            queueElement.entityUUID = resultSet.getString("entityUUID");
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<EntitySpawnQueueElement> entitySpawnQueueElements) throws SQLException {
        if (entitySpawnQueueElements == null || entitySpawnQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, entityUUID, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntitySpawnQueueElement element : entitySpawnQueueElements) {
                pstmt.setString(1, element.entityName);
                pstmt.setString(2, element.entityUUID);
                pstmt.setDouble(3, element.x);
                pstmt.setDouble(4, element.y);
                pstmt.setDouble(5, element.z);
                pstmt.setInt(6, element.dimensionId);
                pstmt.setTimestamp(7, new Timestamp(element.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }
}
