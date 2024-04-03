package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.Commands.HeatMap.HeatMapUpdater;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerUpdater;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class PlayerMovementLogger extends GenericLoggerPositional {

    // This class logs three items to the same database.
    // 1. Player movement every n ticks. By default, n = 100 ticks.
    // 2. Player teleporation between dimensions. Prevents users from evading the above detector by switching dims very
    // quickly.
    // 3. Player login, prevents the user from being logged into a dimension and quickly switching dims, this would
    // cause the dimension
    // to load, which we want to keep track of.

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s was at [%f, %f, %f] in dimension %d at %s",
            rs.getString("playerName"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getInt("dimensionID"),
            rs.getString("timestamp"));
    }


    public PlayerMovementLogger() {
        new TrackPlayerUpdater();
        new HeatMapUpdater();

        FMLCommonHandler.instance()
            .bus()
            .register(this);
        loggerList.add(this);
    }

    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            // Create table if not exists
            try (PreparedStatement pstmt = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Events (" +
                    "playerName TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "dimensionID INTEGER DEFAULT " + TemporaUtils.defaultDimID() + "," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");")) {
                pstmt.execute();
            }

            // Execute index creation statements
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_playerName ON Events(playerName);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_x ON Events(x);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_z ON Events(z);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_dimensionID ON Events(dimensionID);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON Events(timestamp);");
            }

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

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (isClientSide()) return;
        if (!(event.entity instanceof EntityPlayerMP player)) return;

        saveData(player);
    }

    private void saveData(final EntityPlayerMP player) {
        try {
            final String sql = "INSERT INTO Events(playerName, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, player.getDisplayName());
            pstmt.setDouble(2, player.posX);
            pstmt.setDouble(3, player.posY);
            pstmt.setDouble(4, player.posZ);
            pstmt.setInt(5, player.worldObj.provider.dimensionId);
            pstmt.executeUpdate();

        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
