package com.colen.tempora.loggers.entity_position;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.rendering.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntityPositionLogger;
    }

    @Override
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        RenderUtils.sortByDistanceDescending(eventsToRenderInWorld, e);

        for (GenericQueueElement element : eventsToRenderInWorld) {
            if (element instanceof EntityPositionQueueElement epqh) {
                Entity entity = EntityList.createEntityByName(epqh.entityName, Minecraft.getMinecraft().theWorld);

                // Render mob
                RenderUtils.renderEntityInWorld(entity, epqh.x, epqh.y, epqh.z, epqh.rotationYaw, epqh.rotationPitch);

                // Render bounding box (optional, matches location)
                RenderUtils.renderEntityAABBInWorld(entity, epqh.x, epqh.y, epqh.z, 0, 1.0, 0);
            }
        }
    }

    private static int entityMovementLoggingInterval;

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        entityMovementLoggingInterval = config.getInt(
            "entity_position_logging_interval",
            getSQLTableName(),
            500,
            1,
            Integer.MAX_VALUE,
            "How frequently are non-player entity locations recorded to the database, measured in ticks (20 ticks per second)?");
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            EntityPositionQueueElement queueElement = new EntityPositionQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.entityName = resultSet.getString("entityName");
            queueElement.entityUUID = resultSet.getString("entityUUID");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);

        }

        return eventList;
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("entityName", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("entityUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("rotationYaw", "REAL", "NOT NULL DEFAULT 0"),
            new ColumnDef("rotationPitch", "REAL", "NOT NULL DEFAULT 0"));
    }

    @Override
    public void threadedSaveEvents(List<EntityPositionQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, entityUUID, rotationYaw, rotationPitch, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntityPositionQueueElement element : queueElements) {
                pstmt.setString(1, element.entityName);
                pstmt.setString(2, element.entityUUID);
                pstmt.setFloat(3, element.rotationYaw);
                pstmt.setFloat(4, element.rotationPitch);
                pstmt.setDouble(5, element.x);
                pstmt.setDouble(6, element.y);
                pstmt.setDouble(7, element.z);
                pstmt.setInt(8, element.dimensionId);
                pstmt.setTimestamp(9, new Timestamp(element.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
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
        if (event.entity instanceof EntityItem) return;
        if (event.entity instanceof EntityXPOrb) return;

        EntityPositionQueueElement queueElement = new EntityPositionQueueElement();
        queueElement.x = event.entityLiving.posX;
        queueElement.y = event.entityLiving.posY;
        queueElement.z = event.entityLiving.posZ;
        queueElement.dimensionId = event.entityLiving.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.rotationYaw = event.entityLiving.rotationYaw;
        queueElement.rotationPitch = event.entityLiving.rotationPitch;

        queueElement.entityName = event.entityLiving.getCommandSenderName();
        queueElement.entityUUID = event.entityLiving.getUniqueID()
            .toString();

        queueEvent(queueElement);
    }

}
