package com.colen.tempora.logging.loggers.item_use;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger extends GenericPositionalLogger<ItemUseQueueElement> {

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();

        while (resultSet.next()) {

            ItemUseQueueElement queueElement = new ItemUseQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");

            queueElement.playerName = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
            queueElement.itemID = resultSet.getInt("itemID");
            queueElement.itemMetadata = resultSet.getInt("itemMetadata");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void initTable() {
        try {
            positionalLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getLoggerName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "playerUUID TEXT NOT NULL,"
                        + "itemID INTEGER NOT NULL,"
                        + "itemMetadata INTEGER NOT NULL,"
                        + "x REAL NOT NULL,"
                        + "y REAL NOT NULL,"
                        + "z REAL NOT NULL,"
                        + "dimensionID INTEGER DEFAULT 0 NOT NULL,"
                        + "timestamp DATETIME NOT NULL);")
                .execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(ItemUseQueueElement itemUseQueueElement) {
        try {
            final String sql = "INSERT INTO " + getLoggerName()
                + "(playerUUID, itemID, itemMetadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, itemUseQueueElement.playerName);
            pstmt.setInt(2, itemUseQueueElement.itemID);
            pstmt.setInt(3, itemUseQueueElement.itemMetadata);
            pstmt.setDouble(4, itemUseQueueElement.x);
            pstmt.setDouble(5, itemUseQueueElement.y);
            pstmt.setDouble(6, itemUseQueueElement.z);
            pstmt.setInt(7, itemUseQueueElement.dimensionId);
            pstmt.setTimestamp(8, new Timestamp(itemUseQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
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

        ItemUseQueueElement queueElement = new ItemUseQueueElement();

        queueElement.x = player.posX;
        queueElement.y = player.posY;
        queueElement.z = player.posZ;
        queueElement.dimensionId = world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.playerName = player.getUniqueID()
            .toString();

        if (usedItem != null) {
            queueElement.itemID = Item.getIdFromItem(usedItem.getItem());
            queueElement.itemMetadata = usedItem.getItemDamage();
        } else {
            queueElement.itemID = 0;
            queueElement.itemMetadata = 0;
        }

        queueEvent(queueElement);
    }

}
