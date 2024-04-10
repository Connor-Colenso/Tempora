package com.myname.mymodid.PositionalEvents.Loggers.PlayerMovement;

import static com.myname.mymodid.Config.loggingIntervals;
import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.Commands.HeatMap.HeatMapUpdater;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerUpdater;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class PlayerMovementLogger extends GenericPositionalLogger<PlayerMovementQueueElement> {

    // This class logs three items to the same database.
    // 1. Player movement every n ticks. By default, n = 200 ticks.
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
    protected IMessage generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<PlayerMovementQueueElement> eventList = new ArrayList<>();
        int counter = 0;

        while (resultSet.next() && counter < MAX_DATA_ROWS_PER_PACKET) {
            double x = resultSet.getDouble("x");
            double y = resultSet.getDouble("y");
            double z = resultSet.getDouble("z");

            PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement(x, y, z, resultSet.getInt("dimensionID"));
            queueElement.playerUUID = resultSet.getString("playerName");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
            counter++;
        }

        PlayerMovementPacketHandler packet = new PlayerMovementPacketHandler();
        packet.eventList = eventList;

        return packet;
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
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(playerName, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, playerMovementQueueElement.playerUUID);
            pstmt.setDouble(2, playerMovementQueueElement.x);
            pstmt.setDouble(3, playerMovementQueueElement.y);
            pstmt.setDouble(4, playerMovementQueueElement.z);
            pstmt.setInt(5, playerMovementQueueElement.dimensionId);
            pstmt.setTimestamp(6, new Timestamp(playerMovementQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
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
        queueElement.playerUUID = player.getUniqueID().toString();

        eventQueue.add(queueElement);
    }

}
