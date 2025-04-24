package com.colen.tempora.logging.loggers.player_interact_with_inventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.logging.loggers.ISerializable;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

public class PlayerInteractWithInventoryLogger
    extends GenericPositionalLogger<PlayerInteractWithInventoryQueueElement> {

    public PlayerInteractWithInventoryLogger() {
        registerLogger(this);
        // No event logging needed, so we override the constructor here.
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
            queueElement.stacksize = rs.getInt("stacksize");
            eventList.add(queueElement);
        }
        return eventList;
    }

    @Override
    public void initTable() {
        try {
            PreparedStatement statement = positionalLoggerDBConnection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + getLoggerName()
                    + " (id INTEGER PRIMARY KEY AUTOINCREMENT, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, dimensionID INTEGER NOT NULL, timestamp DATETIME NOT NULL, containerName TEXT NOT NULL, interactionType TEXT NOT NULL, playerUUID TEXT NOT NULL, itemId INTEGER NOT NULL, itemMetadata INTEGER NOT NULL, stacksize INTEGER NOT NULL);");
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(PlayerInteractWithInventoryQueueElement element) {
        try {
            PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(
                "INSERT INTO " + getLoggerName()
                    + " (x, y, z, dimensionID, timestamp, containerName, interactionType, itemId, itemMetadata, playerUUID, stacksize) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
            pstmt.setInt(11, element.stacksize);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void playerInteractedWithInventory(EntityPlayer playerMP, Container container, ItemStack itemStack, Direction direction, TileEntity tileEntity) {

        PlayerInteractWithInventoryQueueElement queueElement = new PlayerInteractWithInventoryQueueElement();
        if (tileEntity != null) {
            queueElement.x = tileEntity.xCoord;
            queueElement.y = tileEntity.yCoord;
            queueElement.z = tileEntity.zCoord;
            queueElement.containerName = tileEntity.getClass().getSimpleName();
        } else {
            // Backup
            queueElement.x = playerMP.posX;
            queueElement.y = playerMP.posY;
            queueElement.z = playerMP.posZ;
            queueElement.containerName = container.getClass().getSimpleName();
        }

        queueElement.dimensionId = playerMP.dimension;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.playerUUID = playerMP.getUniqueID()
            .toString();
        queueElement.interactionType = direction == Direction.ToPlayer ? "Remove" : "Add";
        queueElement.itemId = Item.getIdFromItem(itemStack.getItem());
        queueElement.itemMetadata = itemStack.getItemDamage();
        queueElement.stacksize = itemStack.stackSize;

        queueEvent(queueElement);
    }

    // Never change the values here.
    public enum Direction {
        ToPlayer(0),
        FromPlayer(1);

        private final int id;

        Direction(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }
}
