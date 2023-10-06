package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger {

    private Connection conn;
    private static final String url = "jdbc:sqlite:./explosionEvents.db";

    public ExplosionLogger() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            conn = DriverManager.getConnection(url);
            String sql = "CREATE TABLE IF NOT EXISTS ExplosionEvents (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "x REAL NOT NULL,"
                + "y REAL NOT NULL,"
                + "z REAL NOT NULL,"
                + "strength REAL NOT NULL,"
                + "exploder TEXT,"
                + "dimensionID INTEGER DEFAULT 0,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.world;
        float strength = event.explosion.explosionSize;
        double x = event.explosion.explosionX;
        double y = event.explosion.explosionY;
        double z = event.explosion.explosionZ;
        Entity exploder = event.explosion.getExplosivePlacedBy();
        String exploderName = (exploder != null) ? exploder.getCommandSenderName() : "Unknown";

        try {
            String sql = "INSERT INTO ExplosionEvents(x, y, z, strength, exploder, dimensionID) VALUES(?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, y);
            pstmt.setDouble(3, z);
            pstmt.setFloat(4, strength);
            pstmt.setString(5, exploderName);
            pstmt.setInt(6, world.provider.dimensionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void closeDatabase() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
