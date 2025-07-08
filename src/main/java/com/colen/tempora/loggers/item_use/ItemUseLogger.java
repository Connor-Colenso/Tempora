package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger extends GenericPositionalLogger<ItemUseQueueElement> {

    @Override
    public String getSQLTableName() {
        return "ItemUseLogger";
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

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
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("itemID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("itemMetadata", "INTEGER", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void threadedSaveEvents(List<ItemUseQueueElement> itemUseQueueElements) throws SQLException {
        if (itemUseQueueElements == null || itemUseQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, itemID, itemMetadata, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (ItemUseQueueElement elem : itemUseQueueElements) {
                pstmt.setString(1, elem.playerName);
                pstmt.setInt(2, elem.itemID);
                pstmt.setInt(3, elem.itemMetadata);
                pstmt.setDouble(4, elem.x);
                pstmt.setDouble(5, elem.y);
                pstmt.setDouble(6, elem.z);
                pstmt.setInt(7, elem.dimensionId);
                pstmt.setTimestamp(8, new Timestamp(elem.timestamp));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemInteract(final @NotNull PlayerInteractEvent event) {
        // Server side only.
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            logItemUse(event.entityPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemUseStart(final @NotNull PlayerUseItemEvent.Start event) {
        if (event.isCanceled()) return;

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
