package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ItemUseLogger extends GenericPositionalLogger<ItemUseEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ITEM_USE;
    }

    @Override
    public @NotNull String getShortName() {
        return "use";
    }

    @Override
    public @NotNull ItemUseEventInfo newEventInfo() {
        return new ItemUseEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemInteract(final @NotNull PlayerInteractEvent event) {
        // Server side only.
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            logItemUse(event.entityPlayer, event.entityPlayer.getCurrentEquippedItem());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onItemUseStart(final @NotNull PlayerUseItemEvent.Start event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        logItemUse(event.entityPlayer, event.entityPlayer.getCurrentEquippedItem());
    }

    /** Logs a player using an item (or their empty hand), at the player's position. */
    public void logItemUse(final @NotNull EntityPlayer player, final @Nullable ItemStack usedItem) {
        if (!isEnabledByConfig) return;

        ItemUseEventInfo eventInfo = new ItemUseEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();

        if (usedItem != null && usedItem.getItem() != null) {
            eventInfo.itemID = Item.getIdFromItem(usedItem.getItem());
            eventInfo.itemMetadata = usedItem.getItemDamage();
        } else {
            eventInfo.itemID = 0;
            eventInfo.itemMetadata = 0;
        }

        logEvent(eventInfo, player.worldObj.provider.dimensionId, player.posX, player.posY, player.posZ);
    }

}
