package com.myname.mymodid.Loggers;

import static com.myname.mymodid.Config.loggingIntervals;
import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.Commands.HeatMap.HeatMapUpdater;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerUpdater;
import com.myname.mymodid.QueueElement.PlayerMovementQueueElement;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class PlayerMovementLogger extends GenericLoggerPositional<PlayerMovementQueueElement> {

    // This class logs three items to the same database.
    // 1. Player movement every n ticks. By default, n = 100 ticks.
    // 2. Player teleportation between dimensions. Prevents users from evading the above detector by switching dims very
    // quickly.
    // 3. Player login, prevents the user from being logged into a dimension and quickly switching dims, this would
    // cause the dimension
    // to load, which we want to keep track of.

    private int playerMovementLoggingInterval;

    @Override
    public void handleConfig(Configuration config) {
        playerMovementLoggingInterval = config.getInt(
            "playerMovementLoggingInterval",
            loggingIntervals,
            200,
            1,
            Integer.MAX_VALUE,
            "How often player location is recorded to the database. Measured in ticks (20/second).");
    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s was at [%.1f, %.1f, %.1f] in dimension %d at %s",
            rs.getString("playerName"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getInt("dimensionID"),
            rs.getString("timestamp"));
    }

    public PlayerMovementLogger() {
        super();
        new TrackPlayerUpdater();
        new HeatMapUpdater();
    }

    @Override
    public void initTable() {
        try {
            positionLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTableName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "playerName TEXT NOT NULL,"
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
    public void threadedSaveEvent(PlayerMovementQueueElement playerMovementQueueElement) {

    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        if (isClientSide()) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getTickCounter() % playerMovementLoggingInterval != 0) return;

        PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement(
            player.posX,
            player.posY,
            player.posZ,
            player.worldObj.provider.dimensionId);
        queueElement.playerName = player.getDisplayName();

        eventQueue.add(queueElement);
    }

}
