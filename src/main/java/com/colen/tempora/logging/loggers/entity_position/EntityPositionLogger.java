package com.colen.tempora.logging.loggers.entity_position;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionQueueElement> {

    @Override
    public String getSQLTableName() {
        return "EntityPositionLogger";
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
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {
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
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(new ColumnDef("entityName", "TEXT", "NOT NULL"));
    }

    @Override
    public void threadedSaveEvents(List<EntityPositionQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql)) {
            for (EntityPositionQueueElement element : queueElements) {
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

        queueEvent(queueElement);
    }

}
