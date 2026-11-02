package com.colen.tempora.loggers.player_death;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows each player lying where they died, tinted red, with what killed them. */
@SideOnly(Side.CLIENT)
public class PlayerDeathRenderer extends PlayerGhostRenderer<PlayerDeathEventInfo> {

    public PlayerDeathRenderer() {
        super(Color.RED);
    }

    @Override
    protected String playerUUID(PlayerDeathEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected float yaw(PlayerDeathEventInfo event, EventRenderContext context) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(PlayerDeathEventInfo event) {
        return event.rotationPitch;
    }

    // Vanilla draws a dying player tinted red and tipping onto their side, all the way over after 20 ticks of dying.
    @Override
    protected void prepareGhost(EntityOtherPlayerMP ghost, PlayerDeathEventInfo event) {
        ghost.deathTime = 20;
    }

    @Override
    protected List<String> label(List<PlayerDeathEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (PlayerDeathEventInfo event : eventsHere) {
            lines.add(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocalFormatted(
                        "tempora.render.event.killed_by",
                        EventRenderHelpers.killerName(event.killerPlayer, event.killedBy))
                    + " "
                    + EnumChatFormatting.GRAY
                    + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
