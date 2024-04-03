package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger extends GenericLoggerPositional {

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s used %s:%d at [%d, %d, %d] in dimension %d on %s",
            rs.getString("playerName"),
            rs.getString("item"),
            rs.getInt("itemMetadata"),
            rs.getInt("x"),
            rs.getInt("y"),
            rs.getInt("z"),
            rs.getInt("dimensionID"),
            rs.getString("timestamp"));
    }


    @Override
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS Events (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playerName TEXT NOT NULL,"
                + "item TEXT NOT NULL,"
                + "itemMetadata INTEGER,"
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
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return conn;
    }

    @Override
    protected String databaseURL() {
        return TemporaUtils.databaseDirectory() + "itemUseEvents.db";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemInteract(final @NotNull PlayerInteractEvent event) {
        // Server side only.
        if (isClientSide()) return;

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            logItemUse(event.entityPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemUseStart(final @NotNull PlayerUseItemEvent.Start event) {
        logItemUse(event.entityPlayer);
    }

    private void logItemUse(final @NotNull EntityPlayer player) {
        final World world = player.worldObj;
        final ItemStack usedItem = player.getCurrentEquippedItem();

        if (usedItem != null) {
            final int x = (int) player.posX;
            final int y = (int) player.posY;
            final int z = (int) player.posZ;

            try {
                final String sql = "INSERT INTO Events(playerName, item, itemMetadata, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?, ?, ?)";
                final PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, player.getDisplayName());
                pstmt.setString(
                    2,
                    usedItem.getItem()
                        .getUnlocalizedName());
                pstmt.setInt(3, usedItem.getItemDamage());
                pstmt.setInt(4, x);
                pstmt.setInt(5, y);
                pstmt.setInt(6, z);
                pstmt.setInt(7, world.provider.dimensionId);
                pstmt.executeUpdate();
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
