package com.colen.tempora.logging.loggers.entity_death;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.isUUID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathQueueElement> {

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
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
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

            queueElement.timestamp = resultSet.getTimestamp("timestamp").getTime();

            eventList.add(queueElement);
        }

        return eventList;
    }


    @Override
    public void initTable() {
        try {
            positionalLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getLoggerName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "entityName TEXT NOT NULL,"
                        + "killedBy TEXT,"
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
    public void threadedSaveEvent(EntityDeathQueueElement entityDeathQueueElement) throws SQLException {
        final String sql = "INSERT INTO " + getLoggerName()
            + "(entityName, killedBy, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?)";
        final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
        pstmt.setString(1, entityDeathQueueElement.nameOfDeadMob); // Name of the mob
        pstmt.setString(2, entityDeathQueueElement.killedBy); // Who killed it
        pstmt.setDouble(3, entityDeathQueueElement.x); // X coordinate
        pstmt.setDouble(4, entityDeathQueueElement.y); // Y coordinate
        pstmt.setDouble(5, entityDeathQueueElement.z); // Z coordinate
        pstmt.setInt(6, entityDeathQueueElement.dimensionId); // Dimension ID
        pstmt.setTimestamp(7, new Timestamp(entityDeathQueueElement.timestamp)); // Timestamp of death
        pstmt.executeUpdate();
    }
}
