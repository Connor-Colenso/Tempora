package com.colen.tempora.loggers.player_position;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shows the player at each place they were recorded, with a trail joining one player's places in the order they were
 * at them. Each player faces the next place they went to.
 */
@SideOnly(Side.CLIENT)
public class PlayerPositionRenderer extends PlayerGhostRenderer<PlayerPositionEventInfo> {

    // Where each event's player went next, for this frame.
    private final Map<PlayerPositionEventInfo, PlayerPositionEventInfo> nextPlace = new IdentityHashMap<>();

    public PlayerPositionRenderer() {
        super(new Color(0, 220, 220));
    }

    @Override
    public void renderWorld(List<PlayerPositionEventInfo> events, EventRenderContext context) {
        nextPlace.clear();
        for (List<PlayerPositionEventInfo> path : EventRenderHelpers.groupBy(events, event -> event.playerUUID)) {
            for (int i = 0; i + 1 < path.size(); i++) {
                nextPlace.put(path.get(i), path.get(i + 1));
            }
            EventRenderHelpers.renderPath(path, colour);
        }

        super.renderWorld(events, context);
    }

    @Override
    protected float yaw(PlayerPositionEventInfo event, EventRenderContext context) {
        PlayerPositionEventInfo next = nextPlace.get(event);
        // Facing straight up or down a ladder has no direction, so face the camera then too.
        if (next == null || (next.x == event.x && next.z == event.z)) return super.yaw(event, context);

        return EventRenderContext.yawTowards(event.x, event.z, next.x, next.z);
    }

    @Override
    protected String playerUUID(PlayerPositionEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected List<String> label(List<PlayerPositionEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (PlayerPositionEventInfo event : eventsHere) {
            lines.add(EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
