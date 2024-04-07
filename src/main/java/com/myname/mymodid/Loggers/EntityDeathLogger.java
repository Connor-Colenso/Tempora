package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.myname.mymodid.QueueElement.EntityDeathQueueElement;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericLoggerPositional<EntityDeathQueueElement> {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return; // No players allowed here, this is for mobs only.
        if (event.isCanceled()) return;

        EntityDeathQueueElement queueElement = new EntityDeathQueueElement(
            event.entity.posX,
            event.entity.posY,
            event.entity.posZ,
            event.entity.dimension);
        queueElement.nameOfDeadMob = event.entityLiving.getCommandSenderName(); // Gets the mob name, weirdly.

        // Get what killed it.
        Entity trueSource = event.source.getEntity();
        if (trueSource != null) {
            if (trueSource instanceof EntityPlayerMP player) {
                // This is specific for players
                queueElement.killedBy = player.getDisplayName();
            } else {
                // For non-player entities
                queueElement.killedBy = "[" + trueSource.getClass()
                    .getSimpleName() + "]";
            }
        } else {
            queueElement.killedBy = "[" + event.source.damageType + "]";
        }

        eventQueue.add(queueElement);
    }

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s spawned at [%.1f, %.1f, %.1f] in dimension %d at %d",
            rs.getString("entityName"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getInt("dimensionID"),
            rs.getLong("timestamp"));
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
    public void threadedSaveEvent(EntityDeathQueueElement entityDeathQueueElement) {

    }

}
