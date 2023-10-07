package com.myname.mymodid.Loggers;

import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class ExplosionLogger extends GenericLogger{

    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS ExplosionEvents (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "strength REAL NOT NULL,"
                + "exploder TEXT,"
                + "dimensionID INTEGER DEFAULT 0,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return conn;
    }

    @Override
    protected String databaseURL() {
        return TemporaUtils.databaseDirectory() + "explosionEvents.db";
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onExplosion(final @NotNull ExplosionEvent.Detonate event) {
        // Server side only.
        if (isClientSide()) return;

        final World world = event.world;
        final float strength = event.explosion.explosionSize;
        final double x = Math.round(event.explosion.explosionX);
        final double y = Math.round(event.explosion.explosionY);
        final double z = Math.round(event.explosion.explosionZ);
        final Entity exploder = event.explosion.getExplosivePlacedBy();
        final String exploderName = (exploder != null) ? exploder.getCommandSenderName() : "Unknown";

        try {
            final String sql = "INSERT INTO ExplosionEvents(x, y, z, strength, exploder, dimensionID) VALUES(?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, y);
            pstmt.setDouble(3, z);
            pstmt.setFloat(4, strength);
            pstmt.setString(5, exploderName);
            pstmt.setInt(6, world.provider.dimensionId);
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
