package com.colen.tempora.loggers.inventory;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

// Logs items players move in and out of containers. The tracking lives in InventoryTransaction, which wraps every
// relevant packet (see InventoryTransactionTracker), so vanilla, ModularUI and AE2 GUIs all go through the same code
public class InventoryLogger extends GenericPositionalLogger<InventoryEventInfo> {

    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    private static boolean logNBT;

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.INVENTORY;
    }

    @Override
    public @NotNull String getShortName() {
        return "inventory";
    }

    @Override
    public @NotNull InventoryEventInfo newEventInfo() {
        return new InventoryEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    // Items dropped or thrown out while a container is open need telling apart from items that went into it. Highest
    // priority and receives cancelled events so an item another mod merges away still counts
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    @SuppressWarnings("unused")
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!isEnabledByConfig) return;
        if (event.entity instanceof EntityItem entityItem) {
            InventoryTransactionTracker.onItemEntitySpawned(entityItem);
        }
    }

    // Logs change of an item going into (positive) or out of (negative) a container we can't see ourselves, e.g. a
    // drawer clicked in the world instead of through a GUI
    public void logReportedChange(EntityPlayer player, ItemStack item, long change, ContainerHost container) {
        if (!isEnabledByConfig) return;

        // Copy, a transaction might hold onto it until the current packet is done
        ItemKey key = ItemKey.snapshotOf(item);
        if (key == null || change == 0) return;

        logChange(
            player,
            container.name(),
            container.dimension(player),
            container.x(player),
            container.y(player),
            container.z(player),
            key,
            change);

        // If a GUI packet is being tracked right now it mustn't log the same items again
        InventoryTransactionTracker.onReportedChange(player, key, change);
    }

    // Logs change of an item going into (positive) or out of (negative) a container
    void logChange(EntityPlayer player, String containerName, int dimension, double x, double y, double z, ItemKey item,
        long change) {
        if (change == 0) return;

        InventoryEventInfo eventInfo = new InventoryEventInfo();
        eventInfo.containerName = containerName;
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.direction = (change > 0 ? Direction.IN_TO_CONTAINER : Direction.OUT_OF_CONTAINER).getDbId();

        eventInfo.itemID = Item.getIdFromItem(item.item);
        eventInfo.itemMetadata = item.meta;
        eventInfo.amount = (int) Math.min(Integer.MAX_VALUE, Math.abs(change));

        eventInfo.encodedNBT = NBTUtils.getEncodedItemNBT(item.item, item.tag, logNBT);

        logEvent(eventInfo, dimension, x, y, z);
    }

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(settingLine(logNBT ? "tempora.logger.setting.nbt_items.on" : "tempora.logger.setting.nbt_items.off"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            true,
            """
                If true, it will log the NBT of every item moved in or out of an inventory, so items that differ only by NBT (tools, enchanted gear, bees, storage cells...) can be told apart.
                WARNING: NBT may be large and this will cause the database to grow much quicker.
                """);
    }

    // Never change these values
    public enum Direction {

        IN_TO_CONTAINER(0, true),
        OUT_OF_CONTAINER(2, false);

        private final int dbId;
        private final boolean addition;

        Direction(int dbId, boolean addition) {
            this.dbId = dbId;
            this.addition = addition;
        }

        public int getDbId() {
            return dbId;
        }

        public boolean isAddition() {
            return addition;
        }

        public static Direction fromDbId(int dbId) {
            for (Direction direction : values()) {
                if (direction.dbId == dbId) return direction;
            }
            return null;
        }
    }
}
