package com.colen.tempora.logging.loggers.player_interact_with_inventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

public class PlayerInteractWithInventoryLogger
    extends GenericPositionalLogger<PlayerInteractWithInventoryQueueElement> {

    @Override
    public String getSQLTableName() {
        return "PlayerInteractWithInventoryLogger";
    }

    @Override
    protected List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("containerName", "TEXT", "NOT NULL"),
            new ColumnDef("interactionType", "TEXT", "NOT NULL"),
            new ColumnDef("playerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("itemId", "INTEGER", "NOT NULL"),
            new ColumnDef("itemMetadata", "INTEGER", "NOT NULL"),
            new ColumnDef("stacksize", "INTEGER", "NOT NULL"));
    }

    public PlayerInteractWithInventoryLogger() {
        registerLogger(this);
        // No event logging needed, so we override the constructor here.
    }

    @Override
    protected ArrayList<ISerializable> generateQueryResults(ResultSet rs) throws SQLException {
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
    public void threadedSaveEvents(List<PlayerInteractWithInventoryQueueElement> elements) throws SQLException {
        if (elements == null || elements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (x, y, z, dimensionID, timestamp, containerName, interactionType, itemId, itemMetadata, playerUUID, stacksize) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerInteractWithInventoryQueueElement element : elements) {
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
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    public void playerInteractedWithInventory(EntityPlayer playerMP, Container container, ItemStack itemStack,
        Direction direction, TileEntity tileEntity) {

        PlayerInteractWithInventoryQueueElement queueElement = new PlayerInteractWithInventoryQueueElement();
        if (tileEntity != null) {
            queueElement.x = tileEntity.xCoord;
            queueElement.y = tileEntity.yCoord;
            queueElement.z = tileEntity.zCoord;
            if (tileEntity instanceof IInventory inventory) {
                queueElement.containerName = inventory.getInventoryName();
            } else {
                queueElement.containerName = tileEntity.getClass()
                    .getSimpleName();
            }
        } else {
            // Backup
            queueElement.x = playerMP.posX;
            queueElement.y = playerMP.posY;
            queueElement.z = playerMP.posZ;
            queueElement.containerName = container.getClass()
                .getSimpleName();
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
