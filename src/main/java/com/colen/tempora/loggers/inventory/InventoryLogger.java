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

/**
 * Logs items players move into and out of containers. The tracking itself lives in {@link InventoryTransaction}, which
 * runs around every relevant packet (see {@link InventoryTransactionTracker}), so vanilla, ModularUI and AE2 GUIs all
 * go through the same code.
 */
public class InventoryLogger extends GenericPositionalLogger<InventoryEventInfo> {

    // The player who moved the items.
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

    // Items dropped or thrown out while a container is open have to be told apart from items that went into it.
    // Highest priority and receiving cancelled events, so an item merged away by another mod is still counted.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    @SuppressWarnings("unused")
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!isEnabledByConfig) return;
        if (event.entity instanceof EntityItem entityItem) {
            InventoryTransactionTracker.onItemEntitySpawned(entityItem);
        }
    }

    /**
     * Logs that {@code change} of an item went into (positive) or came out of (negative) a container that Tempora
     * cannot see by itself, e.g. a drawer or barrel clicked in the world rather than through a GUI.
     */
    public void logReportedChange(EntityPlayer player, ItemStack item, long change, ContainerHost container) {
        if (!isEnabledByConfig) return;

        // Copied, as a transaction may hold on to it until the packet being handled is done.
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

        // If this happened while a GUI packet is being tracked, that packet must not log the same items again.
        InventoryTransactionTracker.onReportedChange(player, key, change);
    }

    /** Logs that {@code change} of an item went into (positive) or came out of (negative) a container. */
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

        if (item.tag == null) {
            eventInfo.encodedNBT = NBTUtils.NO_NBT;
        } else if (logNBT) {
            eventInfo.encodedNBT = NBTUtils.encodeToString(item.tag);
        } else {
            eventInfo.encodedNBT = NBTUtils.NBT_DISABLED;
        }

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

    // Never change the values here.
    public enum Direction {

        IN_TO_CONTAINER(0, true),
        IN_TO_PLAYER(1, true),
        OUT_OF_CONTAINER(2, false),
        OUT_OF_PLAYER(3, false);

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
