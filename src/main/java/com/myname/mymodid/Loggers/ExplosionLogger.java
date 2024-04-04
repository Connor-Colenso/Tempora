package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger extends GenericLoggerPositional {

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "Explosion at [%.1f, %.1f, %.1f] with strength %.1f by %s in dimension %d on %s, closest player at time of explosion: %s, distance: %.1f meters",
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getFloat("strength"),
            rs.getString("exploder"),
            rs.getInt("dimensionID"),
            rs.getString("timestamp"),
            rs.getString("closestPlayer"),
            rs.getDouble("playerDistance"));
    }

    @Override
    public void initTable() {
        try {
            final String sql = "CREATE TABLE IF NOT EXISTS " + getTableName()
                + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "strength REAL NOT NULL,"
                + "exploder TEXT,"
                + "dimensionID INTEGER DEFAULT "
                + TemporaUtils.defaultDimID()
                + ","
                + // TODO Fix
                "closestPlayer TEXT,"
                + "playerDistance REAL,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            positionLoggerDBConnection.prepareStatement(sql)
                .execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onExplosion(final @NotNull ExplosionEvent.Detonate event) {
        if (isClientSide()) return;

        final World world = event.world;
        final float strength = event.explosion.explosionSize;
        final double x = event.explosion.explosionX;
        final double y = event.explosion.explosionY;
        final double z = event.explosion.explosionZ;
        final Entity exploder = event.explosion.getExplosivePlacedBy();
        final String exploderName = (exploder != null) ? exploder.getCommandSenderName() : "Unknown";

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

        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(x, y, z, strength, exploder, dimensionID, closestPlayer, playerDistance) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, y);
            pstmt.setDouble(3, z);
            pstmt.setFloat(4, strength);
            pstmt.setString(5, exploderName);
            pstmt.setInt(6, world.provider.dimensionId);
            pstmt.setString(7, closestPlayerName);
            pstmt.setDouble(8, closestDistance);
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
