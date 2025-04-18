package com.colen.tempora.logging.loggers.entity_death;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathQueueElement> {

    @Override
    public void handleCustomLoggerConfig(Configuration config) {

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
                queueElement.nameOfPlayerWhoKilledMob = player.getUniqueID()
                    .toString();
            } else {
                // For non-player entities
                queueElement.nameOfPlayerWhoKilledMob = "[" + trueSource.getClass()
                    .getSimpleName() + "]";
            }
        } else {
            queueElement.nameOfPlayerWhoKilledMob = "[" + event.source.damageType + "]";
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
            queueElement.timestamp = resultSet.getTimestamp("timestamp")
                .getTime();

            // Optionally add 'killedBy' data if available in your table
            // queueElement.killedBy = resultSet.getString("killedBy");

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
    public void threadedSaveEvent(EntityDeathQueueElement entityDeathQueueElement) {
        try {
            final String sql = "INSERT INTO " + getLoggerName()
                + "(entityName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, entityDeathQueueElement.nameOfDeadMob); // Name of the mob
            pstmt.setDouble(2, entityDeathQueueElement.x); // X coordinate
            pstmt.setDouble(3, entityDeathQueueElement.y); // Y coordinate
            pstmt.setDouble(4, entityDeathQueueElement.z); // Z coordinate
            pstmt.setInt(5, entityDeathQueueElement.dimensionId); // Dimension ID
            pstmt.setTimestamp(6, new Timestamp(entityDeathQueueElement.timestamp)); // Timestamp of death
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
