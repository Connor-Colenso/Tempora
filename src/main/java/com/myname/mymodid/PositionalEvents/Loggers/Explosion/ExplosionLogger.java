package com.myname.mymodid.PositionalEvents.Loggers.Explosion;

import static com.myname.mymodid.TemporaUtils.isClientSide;

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

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected IMessage generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ExplosionQueueElement> eventList = new ArrayList<>();
        int counter = 0;

        while (resultSet.next() && counter < MAX_DATA_ROWS_PER_PACKET) {
            double x = resultSet.getDouble("x");
            double y = resultSet.getDouble("y");
            double z = resultSet.getDouble("z");
            float strength = resultSet.getFloat("strength");
            String exploder = resultSet.getString("exploder");
            int dimensionID = resultSet.getInt("dimensionID");
            String closestPlayer = resultSet.getString("closestPlayer");
            double playerDistance = resultSet.getDouble("playerDistance");

            ExplosionQueueElement queueElement = new ExplosionQueueElement(x, y, z, dimensionID);
            queueElement.strength = strength;
            queueElement.exploderName = exploder;
            queueElement.closestPlayerUUID = closestPlayer;
            queueElement.closestPlayerUUIDDistance = playerDistance;
            queueElement.timestamp = resultSet.getLong("timestamp");

            eventList.add(queueElement);
            counter++;
        }

        ExplosionPacketHandler packet = new ExplosionPacketHandler();
        packet.eventList = eventList;

        return packet;
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
            pstmt.setString(7, explosionQueueElement.closestPlayerUUID);
            pstmt.setDouble(8, explosionQueueElement.closestPlayerUUIDDistance);
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

        ExplosionQueueElement queueElement = new ExplosionQueueElement(x, y, z, world.provider.dimensionId);
        queueElement.strength = strength;
        queueElement.exploderName = exploderName;
        queueElement.closestPlayerUUID = closestPlayerName;
        queueElement.closestPlayerUUIDDistance = closestDistance;
        eventQueue.add(queueElement);
    }

}
