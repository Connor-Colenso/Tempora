package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.QueueElement.ExplosionQueueElement;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "Explosion at [%.1f, %.1f, %.1f] with strength %.1f by %s at %s, closest player at time of explosion: %s, distance: %.1f meters",
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getFloat("strength"),
            rs.getString("exploder"),
            rs.getTimestamp("timestamp"),
            rs.getString("closestPlayer"),
            rs.getDouble("playerDistance"));
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
                        + "exploder TEXT NOT NULL,"
                        + "dimensionID INTEGER DEFAULT 0 NOT NULL,"
                        + "closestPlayer TEXT NOT NULL,"
                        + "playerDistance REAL NOT NULL,"
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
                + "(x, y, z, strength, exploder, dimensionID, closestPlayer, playerDistance, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
        final String exploderName = (exploder != null) ? exploder.getCommandSenderName()
            : TemporaUtils.UNKNOWN_PLAYER_NAME;

        EntityPlayer closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (EntityPlayer player : world.playerEntities) {
            double distance = player.getDistanceSq(x, y, z);
            if (distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }

        String closestPlayerName = closestPlayer != null ? closestPlayer.getDisplayName() : "None";
        closestDistance = Math.sqrt(closestDistance); // Convert from square distance to actual distance

        ExplosionQueueElement queueElement = new ExplosionQueueElement(x, y, z, world.provider.dimensionId);
        queueElement.strength = strength;
        queueElement.exploderName = exploderName;
        queueElement.closestPlayerName = closestPlayerName;
        queueElement.closestPlayerDistance = closestDistance;
        eventQueue.add(queueElement);
    }

}
