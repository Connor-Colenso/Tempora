package com.myname.mymodid.PositionalEvents.Loggers.ItemUse;

import static com.myname.mymodid.TemporaUtils.isClientSide;

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

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

public class ItemUseLogger extends GenericPositionalLogger<ItemUseQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected IMessage generatePacket(ResultSet resultSet) throws SQLException {
        ArrayList<ItemUseQueueElement> eventList = new ArrayList<>();
        int counter = 0;

        while (resultSet.next() && counter < MAX_DATA_ROWS_PER_PACKET) {
            double x = resultSet.getDouble("x");
            double y = resultSet.getDouble("y");
            double z = resultSet.getDouble("z");
            int dimensionID = resultSet.getInt("dimensionID");
            String playerName = resultSet.getString("playerName");
            int itemID = resultSet.getInt("itemID");
            int itemMetadata = resultSet.getInt("itemMetadata");
            long timestamp = resultSet.getLong("timestamp");

            ItemUseQueueElement queueElement = new ItemUseQueueElement(x, y, z, dimensionID);
            queueElement.playerUUID = playerName;
            queueElement.itemID = itemID;
            queueElement.itemMetadata = itemMetadata;
            queueElement.timestamp = timestamp;

            eventList.add(queueElement);
            counter++;
        }

        ItemUsePacketHandler packet = new ItemUsePacketHandler();
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
                        + "playerName TEXT NOT NULL,"
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
            final String sql = "INSERT INTO " + getTableName()
                + "(playerName, item, itemMetadata, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, itemUseQueueElement.playerUUID);
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

        ItemUseQueueElement queueElement = new ItemUseQueueElement(
            player.posX,
            player.posY,
            player.posZ,
            world.provider.dimensionId);

        queueElement.playerUUID = player.getUniqueID()
            .toString();

        if (usedItem != null) {
            queueElement.itemID = Item.getIdFromItem(usedItem.getItem());
            queueElement.itemMetadata = usedItem.getItemDamage();
        } else {
            queueElement.itemID = 0;
            queueElement.itemMetadata = 0;
        }

        eventQueue.add(queueElement);

    }

}
