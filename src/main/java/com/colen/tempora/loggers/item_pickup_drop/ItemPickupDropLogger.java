package com.colen.tempora.loggers.item_pickup_drop;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.util.List;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.inventory.ItemCounts;
import com.colen.tempora.loggers.inventory.ItemKey;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Logs items players pick up off the ground (see {@link #logPickup}), throw out (Q, or dragging out of a GUI), and drop
 * when they die, each at the player. Items moved into and out of containers are the inventory logger's.
 */
public class ItemPickupDropLogger extends GenericPositionalLogger<ItemPickupDropEventInfo> {

    // The player who picked up or dropped the items.
    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    private static boolean logNBT;

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ITEM_PICKUP_DROP;
    }

    @Override
    public @NotNull String getShortName() {
        return "pickup";
    }

    @Override
    public @NotNull ItemPickupDropEventInfo newEventInfo() {
        return new ItemPickupDropEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    /**
     * Logs what a player just took from an item entity on the ground. {@code stack} is the entity's stack from before
     * they touched it, which a pickup shrinks but leaves the item, meta and NBT of. Called by MixinEntityItem around
     * every pickup: neither pickup event can be relied on for this, as Forge's comes before the items move and FML's is
     * skipped when only part of the stack fits.
     */
    public void logPickup(EntityPlayer player, EntityItem entityItem, ItemStack stack, int amountBefore) {
        if (!isEnabledByConfig) return;

        ItemStack after = entityItem.getEntityItem();
        int amountAfter = after == null || entityItem.isDead ? 0 : after.stackSize;
        if (amountAfter >= amountBefore) return;

        // A stack of one, as an emptied stack has no key.
        ItemStack one = stack.copy();
        one.stackSize = 1;
        logItems(player, Action.PICKUP, ItemKey.of(one), amountBefore - amountAfter);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemToss(final @NotNull ItemTossEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        ItemStack stack = event.entityItem.getEntityItem();
        logItems(event.player, Action.DROP, ItemKey.of(stack), stack == null ? 0 : stack.stackSize);
    }

    // Cancelled when a mod keeps the items some other way (e.g. a grave), so nothing reached the ground.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onPlayerDrops(final @NotNull PlayerDropsEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        // One event per kind of item, rather than one per stack.
        ItemCounts dropped = new ItemCounts();
        for (EntityItem entityItem : event.drops) {
            ItemStack stack = entityItem.getEntityItem();
            if (stack != null) dropped.add(ItemKey.of(stack), stack.stackSize);
        }

        for (Map.Entry<ItemKey, Long> entry : dropped.asMap()
            .entrySet()) {
            logItems(event.entityPlayer, Action.DEATH_DROP, entry.getKey(), entry.getValue());
        }
    }

    /** Logs {@code amount} of an item picked up or dropped by a player, at the player, with which way they faced. */
    public void logItems(EntityPlayer player, Action action, ItemKey item, long amount) {
        if (!isEnabledByConfig) return;
        if (item == null || amount <= 0) return;

        ItemPickupDropEventInfo eventInfo = new ItemPickupDropEventInfo();
        eventInfo.action = action.getDbId();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.itemID = Item.getIdFromItem(item.item);
        eventInfo.itemMetadata = item.meta;
        eventInfo.amount = (int) Math.min(Integer.MAX_VALUE, amount);
        eventInfo.encodedNBT = NBTUtils.getEncodedItemNBT(item.item, item.tag, logNBT);
        eventInfo.rotationYaw = player.rotationYaw;
        eventInfo.rotationPitch = player.rotationPitch;

        logEvent(eventInfo, player.worldObj.provider.dimensionId, player.posX, player.posY, player.posZ);
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
                If true, it will log the NBT of every item picked up or dropped, so items that differ only by NBT (tools, enchanted gear, bees, storage cells...) can be told apart.
                WARNING: NBT may be large and this will cause the database to grow much quicker.
                """);
    }

    // Never change the values here.
    public enum Action {

        PICKUP(0, "tempora.event.item_pickup_drop.pickup"),
        DROP(1, "tempora.event.item_pickup_drop.drop"),
        DEATH_DROP(2, "tempora.event.item_pickup_drop.death_drop");

        private final int dbId;
        private final String langKey;

        Action(int dbId, String langKey) {
            this.dbId = dbId;
            this.langKey = langKey;
        }

        public int getDbId() {
            return dbId;
        }

        public String langKey() {
            return langKey;
        }

        public static Action fromDbId(int dbId) {
            for (Action action : values()) {
                if (action.dbId == dbId) return action;
            }
            return null;
        }
    }
}
