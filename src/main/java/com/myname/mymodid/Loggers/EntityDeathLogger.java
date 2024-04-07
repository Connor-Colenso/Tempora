package com.myname.mymodid.Loggers;

import com.myname.mymodid.QueueElement.EntityDeathQueueElement;
import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class EntityDeathLogger extends GenericLoggerPositional<EntityDeathQueueElement> {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return; // No players allowed here, this is for mobs only.
        if (event.isCanceled()) return;

        EntityDeathQueueElement queueElement = new EntityDeathQueueElement(event.entity.posX, event.entity.posY, event.entity.posZ, event.entity.dimension);
        queueElement.nameOfDeadMob = event.entityLiving.getCommandSenderName(); // Gets the mob name, weirdly.

        // Get what killed it.
        Entity trueSource = event.source.getEntity();
        if (trueSource != null) {
            if (trueSource instanceof EntityPlayerMP player) {
                // This is specific for players
                queueElement.killedBy = player.getDisplayName();
            } else {
                // For non-player entities
                queueElement.killedBy = "[" + trueSource.getClass().getSimpleName() + "]";
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
            final String sql = "CREATE TABLE IF NOT EXISTS " + getTableName()
                + " (entityName TEXT NOT NULL,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "dimensionID INTEGER DEFAULT "
                + TemporaUtils.defaultDimID()
                + ","
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            positionLoggerDBConnection.prepareStatement(sql)
                .execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(EntityDeathQueueElement entityDeathQueueElement) {

    }

}
