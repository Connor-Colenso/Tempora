package com.colen.tempora.loggers.inventory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.EventRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Outlines each container, with the items moved in or out circling above it and a ledger of who moved what above those
@SideOnly(Side.CLIENT)
public class InventoryRenderer implements EventRenderer<InventoryEventInfo> {

    private static final Color COLOUR = new Color(167, 32, 204);

    // Radius of the circle items go round when there's more than one
    private static final double ORBIT_RADIUS = 0.45;
    // Radians per tick, about once round every ten seconds
    private static final double ORBIT_SPEED = 0.03;

    // Made once per event not every frame, null for items this client doesn't have. Weak so each goes after
    // ClientEventStore drops its event, but only once the map's next used, so it also gets cleared on world unload
    // since each item holds its world
    private final Map<InventoryEventInfo, EntityItem> items = new WeakHashMap<>();

    @Override
    public void clearCaches() {
        items.clear();
    }

    @Override
    public void renderWorld(List<InventoryEventInfo> events, EventRenderContext context) {
        for (List<InventoryEventInfo> here : EventRenderHelpers.groupBy(events, EventRenderHelpers::blockOf)) {
            Bounds container = Bounds.of(here.get(0));
            RenderUtils.renderRegion(
                container.minX,
                container.minY,
                container.minZ,
                container.maxX,
                container.maxY,
                container.maxZ,
                COLOUR);

            double centreX = (container.minX + container.maxX) / 2;
            double centreZ = (container.minZ + container.maxZ) / 2;
            double radius = here.size() == 1 ? 0 : ORBIT_RADIUS;

            for (int i = 0; i < here.size(); i++) {
                EntityItem item = itemFor(here.get(i), context);
                if (item == null) continue;

                // RenderItem spins and bobs an item by its age
                item.age = (int) context.animationTicks;

                double angle = 2 * Math.PI * i / here.size() + context.animationTicks * ORBIT_SPEED;
                RenderUtils.renderEntityInWorld(
                    item,
                    centreX + radius * Math.cos(angle),
                    container.maxY + 0.1,
                    centreZ + radius * Math.sin(angle),
                    0,
                    0,
                    context.partialTicks);
            }
        }
    }

    @Override
    public void renderText(List<InventoryEventInfo> events, EventRenderContext context) {
        for (List<InventoryEventInfo> here : EventRenderHelpers.groupBy(events, EventRenderHelpers::blockOf)) {
            List<String> lines = new ArrayList<>();
            lines.add(EnumChatFormatting.LIGHT_PURPLE + here.get(0).containerName);

            for (InventoryEventInfo event : here) {
                InventoryLogger.Direction direction = InventoryLogger.Direction.fromDbId(event.direction);
                boolean added = direction != null && direction.isAddition();

                String moved = StatCollector.translateToLocalFormatted(
                    added ? "tempora.render.event.items_in" : "tempora.render.event.items_out",
                    event.amount,
                    EventRenderHelpers.itemName(event.itemID, event.itemMetadata));

                lines.add(
                    (added ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + moved
                        + " "
                        + EnumChatFormatting.GRAY
                        + EventRenderHelpers.playerName(event.playerUUID)
                        + ", "
                        + EventRenderHelpers.timeAgo(event.timestamp));
            }

            Bounds container = Bounds.of(here.get(0));
            EventRenderHelpers.renderLabelAbove(
                lines,
                (container.minX + container.maxX) / 2,
                container.maxY + 0.75,
                (container.minZ + container.maxZ) / 2);
        }
    }

    private @Nullable EntityItem itemFor(InventoryEventInfo event, EventRenderContext context) {
        if (items.containsKey(event)) return items.get(event);

        ItemStack stack = EventRenderHelpers.itemStack(event.itemID, event.itemMetadata);
        EntityItem item = stack == null ? null : new EntityItem(context.world, event.x, event.y, event.z, stack);
        items.put(event, item);
        return item;
    }

    // What to outline. Block containers are logged at the block's corner, ones carried by a player (backpacks, wireless
    // terminals) or that are entities (minecarts) at the entity's feet
    private static final class Bounds {

        final double minX, minY, minZ, maxX, maxY, maxZ;

        private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static Bounds of(InventoryEventInfo event) {
            boolean isBlock = event.x == Math.floor(event.x) && event.y == Math.floor(event.y)
                && event.z == Math.floor(event.z);
            if (isBlock) return new Bounds(event.x, event.y, event.z, event.x + 1, event.y + 1, event.z + 1);

            return new Bounds(event.x - 0.3, event.y, event.z - 0.3, event.x + 0.3, event.y + 1.8, event.z + 0.3);
        }
    }
}
