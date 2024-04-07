package com.myname.mymodid.Loggers;

import com.myname.mymodid.QueueElement.EntityPositionQueueElement;
import com.myname.mymodid.QueueElement.EntitySpawnQueueElement;
import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class EntitySpawnLogger extends GenericLoggerPositional<EntitySpawnQueueElement> {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntitySpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return;
        if (event.isCanceled()) return;

        EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement(event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ, event.entityLiving.dimension);
        queueElement.entityName = event.entityLiving.getCommandSenderName();

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
    public void threadedSaveEvent(EntitySpawnQueueElement entitySpawnQueueElement) {

    }

}
