package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakLogger extends GenericLogger {

    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS BlockBreakEvents (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playerName TEXT NOT NULL,"
                + "blockType TEXT NOT NULL,"
                + "metadata INTEGER,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "dimensionID INTEGER DEFAULT "
                + TemporaUtils.defaultDimID()
                + ","
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }

        return conn;
    }

    @Override
    protected String databaseURL() {
        return TemporaUtils.databaseDirectory() + "blockBreakEvents.db";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (isClientSide()) return;

        if (event.getPlayer() instanceof EntityPlayerMP) {
            try {
                final String sql = "INSERT INTO BlockBreakEvents(playerName, blockType, metadata, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?, ?, ?)";
                final PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(
                    1,
                    event.getPlayer()
                        .getDisplayName());
                pstmt.setString(2, event.block.getUnlocalizedName());
                pstmt.setInt(3, event.blockMetadata);
                pstmt.setInt(4, event.x);
                pstmt.setInt(5, event.y);
                pstmt.setInt(6, event.z);
                pstmt.setInt(7, event.getPlayer().worldObj.provider.dimensionId);
                pstmt.executeUpdate();
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
