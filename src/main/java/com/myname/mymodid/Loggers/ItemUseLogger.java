package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger {

    private Connection conn;
    private static final String url = "jdbc:sqlite:./itemUseEvents.db";

    public ItemUseLogger() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            conn = DriverManager.getConnection(url);
            String sql = "CREATE TABLE IF NOT EXISTS ItemUseEvents (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playerName TEXT NOT NULL,"
                + "item TEXT NOT NULL,"
                + "itemMetadata INTEGER,"
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
    public void onItemInteract(PlayerInteractEvent event) {
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            logItemUse(event.entityPlayer);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onItemUseStart(PlayerUseItemEvent.Start event) {
        logItemUse(event.entityPlayer);
    }

    private void logItemUse(EntityPlayer player) {
        World world = player.worldObj;
        ItemStack usedItem = player.getCurrentEquippedItem();

        if (usedItem != null) {
            int x = (int) player.posX;
            int y = (int) player.posY;
            int z = (int) player.posZ;

            try {
                String sql = "INSERT INTO ItemUseEvents(playerName, item, itemMetadata, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
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
