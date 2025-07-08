package com.colen.tempora.loggers.player_movement;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class PlayerMovementLogger extends GenericPositionalLogger<PlayerMovementQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerMovementLogger;
    }

    // This class logs three items to the same database.
    // 1. Player movement every n ticks. By default, n = 200 ticks.
    // 2. Player teleportation between dimensions. Prevents users from evading the above detector by switching dims very
    // quickly.
    // 3. Player login, prevents the user from being logged into a dimension and quickly switching dims, this would
    // cause the dimension
    // to load, which we want to keep track of.

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA));
    }

    private int playerMovementLoggingInterval;

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        playerMovementLoggingInterval = config.getInt(
            "playerMovementLoggingInterval",
            getSQLTableName(),
            200,
            1,
            Integer.MAX_VALUE,
            "How often player location is recorded by Tempora. Measured in ticks (20/second).");
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");

            queueElement.playerName = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));

            eventList.add(queueElement);
        }

        return eventList;
    }

    public PlayerMovementLogger() {
        super();
    }

    @Override
    public void threadedSaveEvents(List<PlayerMovementQueueElement> playerMovementQueueElements) throws SQLException {
        if (playerMovementQueueElements == null || playerMovementQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerMovementQueueElement elem : playerMovementQueueElements) {
                pstmt.setString(1, elem.playerName);
                pstmt.setDouble(2, elem.x);
                pstmt.setDouble(3, elem.y);
                pstmt.setDouble(4, elem.z);
                pstmt.setInt(5, elem.dimensionId);
                pstmt.setTimestamp(6, new Timestamp(elem.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getTickCounter() % playerMovementLoggingInterval != 0) return;

        PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement();
        queueElement.x = player.posX;
        queueElement.y = player.posY;
        queueElement.z = player.posZ;
        queueElement.dimensionId = player.worldObj.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.playerName = player.getUniqueID()
            .toString();

        queueEvent(queueElement);
    }

}
