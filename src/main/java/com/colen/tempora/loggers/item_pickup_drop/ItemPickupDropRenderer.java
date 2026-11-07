package com.colen.tempora.loggers.item_pickup_drop;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Player where they picked up or dropped items, facing the way they looked and holding the newest, with what they
// picked up (green) or dropped (red) above them. Thrown items get an arrow the way they were thrown
@SideOnly(Side.CLIENT)
public class ItemPickupDropRenderer extends PlayerGhostRenderer<ItemPickupDropEventInfo> {

    private static final Color DROP_ARROW_COLOUR = new Color(231, 76, 60);
    // Vanilla throws items from just below the eyes
    private static final double THROW_HEIGHT = 1.62 - 0.3;
    private static final double DROP_ARROW_LENGTH = 1.5;

    public ItemPickupDropRenderer() {
        super(new Color(46, 204, 113));
    }

    @Override
    protected String playerUUID(ItemPickupDropEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected float yaw(ItemPickupDropEventInfo event, EventRenderContext context) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(ItemPickupDropEventInfo event) {
        return event.rotationPitch;
    }

    @Override
    public void renderWorld(List<ItemPickupDropEventInfo> events, EventRenderContext context) {
        super.renderWorld(events, context);

        // Only thrown items (Q or dragged out of a GUI) go the way the player looked, death drops scatter
        for (ItemPickupDropEventInfo event : events) {
            if (ItemPickupDropLogger.Action.fromDbId(event.action) != ItemPickupDropLogger.Action.DROP) continue;
            EventRenderHelpers.renderLookArrow(
                event.x,
                event.y + THROW_HEIGHT,
                event.z,
                event.rotationYaw,
                event.rotationPitch,
                DROP_ARROW_LENGTH,
                DROP_ARROW_COLOUR);
        }
    }

    @Override
    protected @Nullable ItemStack heldItem(ItemPickupDropEventInfo event) {
        return EventRenderHelpers.itemStack(event.itemID, event.itemMetadata);
    }

    @Override
    protected List<String> label(List<ItemPickupDropEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (ItemPickupDropEventInfo event : eventsHere) {
            ItemPickupDropLogger.Action action = ItemPickupDropLogger.Action.fromDbId(event.action);
            boolean pickedUp = action == ItemPickupDropLogger.Action.PICKUP;

            String key;
            if (pickedUp) {
                key = "tempora.render.event.picked_up";
            } else if (action == ItemPickupDropLogger.Action.DEATH_DROP) {
                key = "tempora.render.event.death_dropped";
            } else {
                key = "tempora.render.event.dropped";
            }

            lines.add(
                (pickedUp ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                    + StatCollector.translateToLocalFormatted(
                        key,
                        event.amount,
                        EventRenderHelpers.itemName(event.itemID, event.itemMetadata))
                    + " "
                    + EnumChatFormatting.GRAY
                    + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
