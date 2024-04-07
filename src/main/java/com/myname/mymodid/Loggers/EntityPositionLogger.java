package com.myname.mymodid.Loggers;

import static com.myname.mymodid.Config.loggingIntervals;
import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import com.myname.mymodid.QueueElement.EntityPositionQueueElement;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityPositionLogger extends GenericLoggerPositional<EntityPositionQueueElement> {

    public static int entityMovementLoggingInterval;

    @Override
    public void handleConfig(Configuration config) {
        entityMovementLoggingInterval = config.getInt(
            "playerMovementLoggingInterval",
            loggingIntervals,
            500,
            1,
            Integer.MAX_VALUE,
            "How often entities location is recorded to the database. Measured in ticks (20/second).");
    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s was at [%.1f, %.1f, %.1f] in dimension %d at %d",
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
    public void threadedSaveEvent(EntityPositionQueueElement entityPositionQueueElement) {

    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entityLiving.ticksExisted % entityMovementLoggingInterval != 0) return; // As an example, track every
                                                                                          // 20 seconds.
        if (event.entityLiving instanceof EntityPlayerMP) return; // Do not track players here, we do this elsewhere.

        EntityPositionQueueElement queueElement = new EntityPositionQueueElement(
            event.entityLiving.posX,
            event.entityLiving.posY,
            event.entityLiving.posZ,
            event.entityLiving.dimension);
        queueElement.entityName = event.entityLiving.getCommandSenderName();

        eventQueue.add(queueElement);

        // try {
        // final String sql = "INSERT INTO " + getTableName()
        // + "(entityName, x, y, z, dimensionID, eventType) VALUES(?, ?, ?, ?, ?, ?)";
        // final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
        // pstmt.setString(1, event.entityLiving.getCommandSenderName());
        // pstmt.setDouble(2, event.entityLiving.posX);
        // pstmt.setDouble(3, event.entityLiving.posY);
        // pstmt.setDouble(4, event.entityLiving.posZ);
        // pstmt.setInt(5, event.entityLiving.worldObj.provider.dimensionId);
        // pstmt.executeUpdate();
        //
        // } catch (final SQLException e) {
        // e.printStackTrace();
        // }
    }

}
