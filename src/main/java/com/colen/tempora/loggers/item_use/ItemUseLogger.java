package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemUseLogger extends GenericPositionalLogger<ItemUseEventInfo> {

    @Override
    public String getLoggerName() {
        return TemporaEvents.ITEM_USE;
    }

    @Override
    public @NotNull ItemUseEventInfo getEventInfoInstance() {
        return new ItemUseEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {

    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
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

        ItemUseEventInfo eventInfo = new ItemUseEventInfo();
        eventInfo.eventID = UUID.randomUUID()
            .toString();
        eventInfo.x = player.posX;
        eventInfo.y = player.posY;
        eventInfo.z = player.posZ;
        eventInfo.dimensionID = world.provider.dimensionId;
        eventInfo.timestamp = System.currentTimeMillis();

        eventInfo.playerUUID = player.getUniqueID()
            .toString();

        if (usedItem != null) {
            eventInfo.itemID = Item.getIdFromItem(usedItem.getItem());
            eventInfo.itemMetadata = usedItem.getItemDamage();
        } else {
            eventInfo.itemID = 0;
            eventInfo.itemMetadata = 0;
        }

        queueEventInfo(eventInfo);
    }

}
