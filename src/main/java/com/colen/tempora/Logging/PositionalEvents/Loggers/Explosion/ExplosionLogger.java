package com.colen.tempora.Logging.PositionalEvents.Loggers.Explosion;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.Logging.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.colen.tempora.Logging.PositionalEvents.Loggers.ISerializable;
import com.colen.tempora.TemporaUtils;
import com.colen.tempora.Utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            ExplosionQueueElement queueElement = new ExplosionQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.strength = resultSet.getFloat("strength");
            queueElement.exploderName = PlayerUtils.UUIDToName(resultSet.getString("exploderUUID"));
            queueElement.closestPlayerName = PlayerUtils.UUIDToName(resultSet.getString("closestPlayerUUID"));
            queueElement.closestPlayerDistance = resultSet.getDouble("playerDistance");
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void initTable() {
        try {
            positionLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTableName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "x REAL NOT NULL,"
                        + "y REAL NOT NULL,"
                        + "z REAL NOT NULL,"
                        + "strength REAL NOT NULL,"
                        + "exploderUUID TEXT NOT NULL,"
                        + "dimensionID INTEGER DEFAULT 0 NOT NULL,"
                        + "closestPlayer TEXT NOT NULL,"
                        + "closestPlayerUUID REAL NOT NULL,"
                        + "timestamp DATETIME NOT NULL);")
                .execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(ExplosionQueueElement explosionQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(x, y, z, strength, exploderUUID, dimensionID, closestPlayerUUID, playerDistance, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setDouble(1, explosionQueueElement.x);
            pstmt.setDouble(2, explosionQueueElement.y);
            pstmt.setDouble(3, explosionQueueElement.z);
            pstmt.setFloat(4, explosionQueueElement.strength);
            pstmt.setString(5, explosionQueueElement.exploderName);
            pstmt.setInt(6, explosionQueueElement.dimensionId);
            pstmt.setString(7, explosionQueueElement.closestPlayerName);
            pstmt.setDouble(8, explosionQueueElement.closestPlayerDistance);
            pstmt.setTimestamp(9, new Timestamp(explosionQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(final @NotNull ExplosionEvent.Detonate event) {
        if (isClientSide()) return;

        final World world = event.world;
        final float strength = event.explosion.explosionSize;
        final double x = event.explosion.explosionX;
        final double y = event.explosion.explosionY;
        final double z = event.explosion.explosionZ;
        final Entity exploder = event.explosion.getExplosivePlacedBy();
        final String exploderName = (exploder != null) ? exploder.getUniqueID()
            .toString() : TemporaUtils.UNKNOWN_PLAYER_NAME;

        EntityPlayer closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (EntityPlayer player : world.playerEntities) {
            double distance = player.getDistanceSq(x, y, z);
            if (distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }

        String closestPlayerName = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : TemporaUtils.UNKNOWN_PLAYER_NAME;
        closestDistance = Math.sqrt(closestDistance); // Convert from square distance to actual distance

        ExplosionQueueElement queueElement = new ExplosionQueueElement();
        queueElement.x = event.explosion.explosionX;
        queueElement.y = event.explosion.explosionY;
        queueElement.z = event.explosion.explosionZ;
        queueElement.dimensionId = world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.strength = strength;
        queueElement.exploderName = exploderName;
        queueElement.closestPlayerName = closestPlayerName;
        queueElement.closestPlayerDistance = closestDistance;
        eventQueue.add(queueElement);
    }

}
