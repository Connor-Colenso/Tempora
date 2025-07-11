package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.PlayerUtils.isUUID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntityDeathLogger;
    }

    @Override
    public void renderEventInWorld(RenderWorldLastEvent e) {

    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return; // No players allowed here, this is for mobs only.
        if (event.isCanceled()) return;

        EntityDeathQueueElement queueElement = new EntityDeathQueueElement();
        queueElement.x = event.entity.posX;
        queueElement.y = event.entity.posY;
        queueElement.z = event.entity.posZ;
        queueElement.dimensionId = event.entity.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.nameOfDeadMob = event.entityLiving.getCommandSenderName(); // Gets the mob name, weirdly.
        queueElement.entityUUID = event.entityLiving.getUniqueID()
            .toString();

        // Get what killed it.
        Entity trueSource = event.source.getEntity();
        if (trueSource != null) {
            if (trueSource instanceof EntityPlayerMP player) {
                // This is specific for players
                queueElement.killedBy = player.getUniqueID()
                    .toString();
            } else {
                // For non-player entities
                queueElement.killedBy = "[" + trueSource.getClass()
                    .getSimpleName() + "]";
            }
        } else {
            queueElement.killedBy = "[" + event.source.damageType + "]";
        }

        queueEvent(queueElement);
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {
            EntityDeathQueueElement queueElement = new EntityDeathQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.nameOfDeadMob = resultSet.getString("entityName");
            queueElement.entityUUID = resultSet.getString("entityUUID");

            String killedBy = resultSet.getString("killedBy");
            if (isUUID(killedBy)) {
                queueElement.killedBy = PlayerUtils.UUIDToName(killedBy);
            } else {
                queueElement.killedBy = killedBy;
            }

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
            new ColumnDef("killedBy", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA));
    }

    @Override
    public void threadedSaveEvents(List<EntityDeathQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, entityUUID, killedBy, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntityDeathQueueElement entity : queueElements) {
                pstmt.setString(1, entity.nameOfDeadMob);
                pstmt.setString(2, entity.entityUUID);
                pstmt.setString(3, entity.killedBy);
                pstmt.setDouble(4, entity.x);
                pstmt.setDouble(5, entity.y);
                pstmt.setDouble(6, entity.z);
                pstmt.setInt(7, entity.dimensionId);
                pstmt.setTimestamp(8, new Timestamp(entity.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

}
