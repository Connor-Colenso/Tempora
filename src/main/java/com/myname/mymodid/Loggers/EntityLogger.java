package com.myname.mymodid.Loggers;

import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class EntityLogger extends GenericLogger {

    public EntityLogger() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        loggerList.add(this);
    }

    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS EntityEvents ("
                + "entityName TEXT NOT NULL,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "dimensionID INTEGER DEFAULT " + TemporaUtils.defaultDimID() + ","
                + "eventType TEXT NOT NULL,"
                + "timestamp BIGINT DEFAULT 0"
                + ");";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }

        return conn;
    }


    @Override
    protected String databaseURL() {
        return TemporaUtils.databaseDirectory() + "entityEvents.db";
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onEntitySpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        saveEntityData(event.entityLiving, "Spawn");
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (isClientSide()) return;
        saveEntityData(event.entityLiving, "Death");
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (isClientSide()) return;
        if (event.entityLiving.ticksExisted % 20 * 20 != 0) return;  // As an example, track every 20 seconds.
        saveEntityData(event.entityLiving, "Movement");
    }

    private void saveEntityData(EntityLivingBase entity, String eventType) {
        try {
            final String sql = "INSERT INTO EntityEvents(entityName, x, y, z, dimensionID, eventType, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, entity.getCommandSenderName());
            pstmt.setDouble(2, entity.posX);
            pstmt.setDouble(3, entity.posY);
            pstmt.setDouble(4, entity.posZ);
            pstmt.setInt(5, entity.worldObj.provider.dimensionId);
            pstmt.setString(6, eventType);
            pstmt.setLong(7, System.currentTimeMillis());
            pstmt.executeUpdate();

        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }
}
