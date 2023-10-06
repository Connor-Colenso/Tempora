package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakLogger {

    private Connection conn;
    private static final String url = "jdbc:sqlite:./blockBreakEvents.db"; // This will save the database in the server
                                                                           // root.

    public BlockBreakLogger() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            conn = DriverManager.getConnection(url);
            String sql = "CREATE TABLE IF NOT EXISTS BlockBreakEvents (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playerName TEXT NOT NULL,"
                + "blockType TEXT NOT NULL,"
                + "metadata INTEGER,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
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
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof EntityPlayerMP) {
            try {
                String sql = "INSERT INTO BlockBreakEvents(playerName, blockType, metadata, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(
                    1,
                    event.getPlayer()
                        .getDisplayName());
                pstmt.setString(2, event.block.getUnlocalizedName()); // This will get the block's unlocalized name,
                                                                      // adjust as needed.
                pstmt.setInt(3, event.blockMetadata);
                pstmt.setInt(4, event.x);
                pstmt.setInt(5, event.y);
                pstmt.setInt(6, event.z);
                pstmt.setInt(7, event.getPlayer().worldObj.provider.dimensionId); // This fetches the dimension ID.
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
