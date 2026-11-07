package com.colen.tempora.loggers.item_use;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Player where they used each item holding the newest, with what they used there above them
@SideOnly(Side.CLIENT)
public class ItemUseRenderer extends PlayerGhostRenderer<ItemUseEventInfo> {

    public ItemUseRenderer() {
        super(new Color(255, 140, 0));
    }

    @Override
    protected String playerUUID(ItemUseEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected @Nullable ItemStack heldItem(ItemUseEventInfo event) {
        return EventRenderHelpers.itemStack(event.itemID, event.itemMetadata);
    }

    @Override
    protected List<String> label(List<ItemUseEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (ItemUseEventInfo event : eventsHere) {
            // Item 0 is an empty hand
            String item = event.itemID == 0 ? StatCollector.translateToLocal("tempora.render.event.empty_hand")
                : EventRenderHelpers.itemName(event.itemID, event.itemMetadata);

            lines.add(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocalFormatted("tempora.render.event.item_used", item)
                    + " "
                    + EnumChatFormatting.GRAY
                    + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
