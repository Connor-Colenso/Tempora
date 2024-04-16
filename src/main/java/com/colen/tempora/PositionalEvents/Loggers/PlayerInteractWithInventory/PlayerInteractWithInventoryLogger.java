package com.colen.tempora.PositionalEvents.Loggers.PlayerInteractWithInventory;

import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.colen.tempora.PositionalEvents.Loggers.ISerializable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.config.Configuration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class PlayerInteractWithInventoryLogger extends GenericPositionalLogger<PlayerInteractWithInventoryQueueElement> {

    public PlayerInteractWithInventoryLogger() {
        loggerList.add(this);
        // No event logging needed, so we override the constructor here.
    }

    @Override
    public void handleConfig(Configuration config) {
        // Implement configuration handling if needed
    }

    @Override
    protected ArrayList<ISerializable> generatePacket(ResultSet rs) throws SQLException {
        ArrayList<ISerializable> eventList = new ArrayList<>();
        while (rs.next()) {
            PlayerInteractWithInventoryQueueElement queueElement = new PlayerInteractWithInventoryQueueElement();
            queueElement.x = rs.getDouble("x");
            queueElement.y = rs.getDouble("y");
            queueElement.z = rs.getDouble("z");
            queueElement.dimensionId = rs.getInt("dimensionID");
            queueElement.timestamp = rs.getLong("timestamp");
            queueElement.containerName = rs.getString("containerName");
            queueElement.playerUUID = rs.getString("playerUUID");
            queueElement.interactionType = rs.getString("interactionType");
            queueElement.itemId = rs.getInt("itemId");
            queueElement.itemMetadata = rs.getInt("itemMetadata");
            eventList.add(queueElement);
        }
        return eventList;
    }


    @Override
    public void initTable() {
        try {
            PreparedStatement statement = positionLoggerDBConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + getTableName() +
                    " (id INTEGER PRIMARY KEY AUTOINCREMENT, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, dimensionID INTEGER NOT NULL, timestamp DATETIME NOT NULL, containerName TEXT NOT NULL, interactionType TEXT NOT NULL, playerUUID TEXT NOT NULL, itemId INTEGER, itemMetadata INTEGER);");
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void threadedSaveEvent(PlayerInteractWithInventoryQueueElement element) {
        try {
            PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(
                "INSERT INTO " + getTableName() + " (x, y, z, dimensionID, timestamp, containerName, interactionType, itemId, itemMetadata, playerUUID) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            pstmt.setDouble(1, element.x);
            pstmt.setDouble(2, element.y);
            pstmt.setDouble(3, element.z);
            pstmt.setInt(4, element.dimensionId);
            pstmt.setTimestamp(5, new Timestamp(element.timestamp));
            pstmt.setString(6, element.containerName);
            pstmt.setString(7, element.interactionType);
            pstmt.setInt(8, element.itemId);
            pstmt.setInt(9, element.itemMetadata);
            pstmt.setString(10, element.playerUUID);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void playerInteractedWithInventory(EntityPlayerMP playerMP, C0EPacketClickWindow packetClickWindow) {
        Container container = playerMP.openContainer;
        if (container == null) return;

        ItemStack itemStack = packetClickWindow.func_149546_g();
        if (itemStack == null) return; // No item was involved in the interaction.

        double x = 0, y = 0, z = 0;
        String containerType = "Unknown";

        // Check if the container is linked to a TileEntity
        if (!container.inventorySlots.isEmpty()) {
            IInventory inventory = container.inventorySlots.get(0).inventory;
            if (inventory instanceof TileEntity tileEntity) {
                x = tileEntity.xCoord;
                y = tileEntity.yCoord;
                z = tileEntity.zCoord;
                containerType = tileEntity.getBlockType().getLocalizedName();
            } else {
                containerType = "[TEMPORA_UNKNOWN_CONTAINER]";
                x = playerMP.posX;
                y = playerMP.posY;
                z = playerMP.posZ;
            }
        }

        PlayerInteractWithInventoryQueueElement queueElement = new PlayerInteractWithInventoryQueueElement();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = playerMP.dimension;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.playerUUID = playerMP.getUniqueID().toString();
        queueElement.containerName = containerType;
        queueElement.interactionType = packetClickWindow.func_149542_h() == 0 ? "Remove" : "Add";
        queueElement.itemId = Item.getIdFromItem(itemStack.getItem());
        queueElement.itemMetadata = itemStack.getItemDamage();

        // todo stacksize

        eventQueue.add(queueElement);
    }

}
