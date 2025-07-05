package com.colen.tempora.logging.loggers.entity_death;

import static com.colen.tempora.TemporaUtils.isClientSide;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathQueueElement> {

    @Override
    public String getSQLTableName() {
        return "EntityDeathLogger";
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
    protected ArrayList<ISerializable> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {
            EntityDeathQueueElement queueElement = new EntityDeathQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.nameOfDeadMob = resultSet.getString("entityName");

            String killedBy = resultSet.getString("killedBy");
            if (isUUID(killedBy)) {
                queueElement.killedBy = PlayerUtils.UUIDToName(killedBy);
            } else {
                queueElement.killedBy = killedBy;
            }

            queueElement.timestamp = resultSet.getTimestamp("timestamp")
                .getTime();

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays
            .asList(new ColumnDef("entityName", "TEXT", "NOT NULL"), new ColumnDef("killedBy", "TEXT", "NOT NULL"));
    }

    @Override
    public void threadedSaveEvents(List<EntityDeathQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, killedBy, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntityDeathQueueElement entity : queueElements) {
                pstmt.setString(1, entity.nameOfDeadMob);
                pstmt.setString(2, entity.killedBy);
                pstmt.setDouble(3, entity.x);
                pstmt.setDouble(4, entity.y);
                pstmt.setDouble(5, entity.z);
                pstmt.setInt(6, entity.dimensionId);
                pstmt.setTimestamp(7, new Timestamp(entity.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

}
