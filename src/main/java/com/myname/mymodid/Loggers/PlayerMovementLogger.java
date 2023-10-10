package com.myname.mymodid.Loggers;

import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerUpdater;
import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class PlayerMovementLogger extends GenericLogger {

    public PlayerMovementLogger() {
        new TrackPlayerUpdater();

        FMLCommonHandler.instance()
            .bus()
            .register(this);
        loggerList.add(this);
    }

    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS PlayerMovementEvents ("
                + "playerName TEXT NOT NULL,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "dimensionID INTEGER DEFAULT " + TemporaUtils.defaultDimID() + ","
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
        return TemporaUtils.databaseDirectory() + "playerMovementEvents.db";
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        // Events are only logged server side every 5 seconds at the start of a tick.
        if (isClientSide()) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;

        // Trigger this tracking every 5 seconds.
        // Todo make this timer changeable in the config.
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getTickCounter() % 100 != 0) return;

        saveData(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (isClientSide()) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;

        saveData(player);
    }

    private void saveData(final EntityPlayerMP player) {
        try {
            final String sql = "INSERT INTO PlayerMovementEvents(playerName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, player.getDisplayName());
            pstmt.setDouble(2, player.posX);
            pstmt.setDouble(3, player.posY);
            pstmt.setDouble(4, player.posZ);
            pstmt.setInt(5, player.worldObj.provider.dimensionId);
            pstmt.setLong(6, System.currentTimeMillis());  // Set the timestamp with millisecond precision
            pstmt.executeUpdate();

        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
