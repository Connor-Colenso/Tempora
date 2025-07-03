package com.colen.tempora.logging.loggers.inventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.colen.tempora.Tempora;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.logging.loggers.generic.ISerializable;
import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

public class InventoryLogger
    extends GenericPositionalLogger<PlayerInteractWithInventoryQueueElement> {

    public static void preLogLogic(EntityPlayer player, Container container, List<Slot> inventorySlots, Map<Integer, ItemStack> snapshot) {
        for (Slot s : inventorySlots) {
            ItemStack before = snapshot.get(s.slotNumber);
            ItemStack after  = s.getStack();

            if (!ItemStack.areItemStacksEqual(before, after)) {
                int beforeCnt = before == null ? 0 : before.stackSize;
                int afterCnt  = after  == null ? 0 : after.stackSize;
                int delta     = afterCnt - beforeCnt;     // + = added, − = removed

                Direction dir = (s.inventory instanceof InventoryPlayer)
                    ? (delta > 0 ? Direction.IN_TO_PLAYER
                    : Direction.OUT_OF_PLAYER)
                    : (delta > 0 ? Direction.IN_TO_CONTAINER
                    : Direction.OUT_OF_CONTAINER);

                Tempora.inventoryLogger.playerInteractedWithInventory(player,
                    delta, after == null ? before : after, dir, s.inventory, container);
            }
        }
    }

    @Override
    public String getSQLTableName() {
        return "InventoryLogger";
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

    public InventoryLogger() {
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
            queueElement.interactionType = rs.getInt("interactionType");
            queueElement.itemId = rs.getInt("itemId");
            queueElement.itemMetadata = rs.getInt("itemMetadata");
            queueElement.stackSize = rs.getInt("stacksize");
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
                pstmt.setInt(7, element.interactionType);
                pstmt.setInt(8, element.itemId);
                pstmt.setInt(9, element.itemMetadata);
                pstmt.setString(10, element.playerUUID);
                pstmt.setInt(11, element.stackSize);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    public void playerInteractedWithInventory(EntityPlayer playerMP, int delta, ItemStack itemStack, Direction dir, IInventory inventory, Container container) {
        ItemStack copyStack = itemStack.copy();
        copyStack.stackSize = Math.abs(delta);

        PlayerInteractWithInventoryQueueElement queueElement = new PlayerInteractWithInventoryQueueElement();

        queueElement.dimensionId = playerMP.dimension;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.playerUUID = playerMP.getUniqueID()
            .toString();
        queueElement.interactionType = dir.getDbId();
        queueElement.itemId = Item.getIdFromItem(copyStack.getItem());
        queueElement.itemMetadata = copyStack.getItemDamage();
        queueElement.stackSize = copyStack.stackSize;

        queueEvent(queueElement);
    }

    // Never change the values here.
    public enum Direction {
        IN_TO_CONTAINER (0,    true),
        IN_TO_PLAYER    (1,    true),
        OUT_OF_CONTAINER(2,    false),
        OUT_OF_PLAYER   (3,    false);

        private final int dbId;
        private final boolean addition;

        Direction(int dbId, boolean addition) {
            this.dbId = dbId;
            this.addition = addition;
        }

        public int  getDbId() { return dbId; }
        public boolean isAddition(){ return addition; }

        public static Direction fromOrdinal(int ordinal) {
            Direction[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return null;
            }
            return values[ordinal];
        }
    }
}
