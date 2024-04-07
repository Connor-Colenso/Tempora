package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.myname.mymodid.QueueElement.ItemUseQueueElement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger extends GenericLoggerPositional<ItemUseQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

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
    public void initTable() {
        try {
            final String sql = "CREATE TABLE IF NOT EXISTS " + getTableName()
                + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
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
            positionLoggerDBConnection.prepareStatement(sql)
                .execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(ItemUseQueueElement itemUseQueueElement) {

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
            player.posX, player.posY, player.posZ,
            world.provider.dimensionId
        );

        queueElement.playerName = player.getDisplayName();

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
