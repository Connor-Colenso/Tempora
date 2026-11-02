package com.colen.tempora.rendering.events;

import static com.colen.tempora.Tempora.LOG;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws a player where they were for each event, e.g. where a command was typed, with a label above their name tag.
 * Several events by one player at one spot (commands typed without moving) share one player and one label.
 */
@SideOnly(Side.CLIENT)
public abstract class PlayerGhostRenderer<E extends PositionalEventInfo> implements EventRenderer<E> {

    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    // Just above the player's name tag.
    private static final double LABEL_HEIGHT = 2.55;

    protected final Color colour;

    // Made once per event rather than every frame, keyed by the newest event at each spot. Null when the player cannot
    // be drawn. Weak, so each goes once ClientEventStore drops its event.
    private final Map<E, EntityOtherPlayerMP> ghosts = new WeakHashMap<>();

    protected PlayerGhostRenderer(Color colour) {
        this.colour = colour;
    }

    protected abstract String playerUUID(E event);

    /** The lines shown above the player, for the events at one spot, oldest first. */
    protected abstract List<String> label(List<E> eventsHere);

    /** What the player is shown holding. */
    protected @Nullable ItemStack heldItem(E event) {
        return null;
    }

    /** Which way the player faces. Towards the camera unless a renderer knows better. */
    protected float yaw(E event, EventRenderContext context) {
        return context.yawTowardsCamera(event.x, event.z);
    }

    /** How far the player's head is tilted, in degrees, down being positive. Level unless a renderer knows better. */
    protected float pitch(E event) {
        return 0;
    }

    /** Changes the player once, as it is made, e.g. to draw them dying. */
    protected void prepareGhost(EntityOtherPlayerMP ghost, E event) {}

    @Override
    public void renderWorld(List<E> events, EventRenderContext context) {
        for (List<E> here : groupsBySpot(events)) {
            E newest = here.get(here.size() - 1);

            EntityOtherPlayerMP ghost = ghostFor(newest, context);
            if (ghost != null) {
                try {
                    RenderUtils.renderEntityInWorld(
                        ghost,
                        newest.x,
                        newest.y,
                        newest.z,
                        yaw(newest, context),
                        pitch(newest),
                        context.partialTicks);
                    RenderUtils.renderEntityAABBInWorld(ghost, colour);
                    continue;
                } catch (RuntimeException | LinkageError e) {
                    LOG.warn(
                        "Could not draw a player for a {} event, showing a box instead.",
                        newest.getLoggerName(),
                        e);
                    ghosts.put(newest, null);
                }
            }

            RenderUtils.renderRegion(
                newest.x - PLAYER_HALF_WIDTH,
                newest.y,
                newest.z - PLAYER_HALF_WIDTH,
                newest.x + PLAYER_HALF_WIDTH,
                newest.y + PLAYER_HEIGHT,
                newest.z + PLAYER_HALF_WIDTH,
                colour);
        }
    }

    @Override
    public void renderText(List<E> events, EventRenderContext context) {
        for (List<E> here : groupsBySpot(events)) {
            E newest = here.get(here.size() - 1);
            EventRenderHelpers.renderLabelAbove(label(here), newest.x, newest.y + LABEL_HEIGHT, newest.z);
        }
    }

    private List<List<E>> groupsBySpot(List<E> events) {
        return EventRenderHelpers
            .groupBy(events, event -> Arrays.asList(EventRenderHelpers.blockOf(event), playerUUID(event)));
    }

    private @Nullable EntityOtherPlayerMP ghostFor(E event, EventRenderContext context) {
        if (ghosts.containsKey(event)) return ghosts.get(event);

        EntityOtherPlayerMP ghost = null;
        try {
            ghost = new EntityOtherPlayerMP(context.world, profileOf(playerUUID(event)));
            ItemStack held = heldItem(event);
            if (held != null) ghost.inventory.mainInventory[ghost.inventory.currentItem] = held;
            prepareGhost(ghost, event);
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Could not make a player for a {} event, showing a box instead.", event.getLoggerName(), e);
            ghost = null;
        }

        ghosts.put(event, ghost);
        return ghost;
    }

    // The real player's profile while they are near enough to be seen, so the ghost wears their skin. Otherwise a
    // default skin, under their name.
    private static GameProfile profileOf(String playerUUID) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(playerUUID);
        } catch (IllegalArgumentException | NullPointerException notAUUID) {
            // Shown under whatever was stored instead.
        }

        if (uuid != null) {
            EntityPlayer online = EventRenderHelpers.onlinePlayer(uuid);
            if (online != null) return online.getGameProfile();
        }

        return new GameProfile(uuid, EventRenderHelpers.playerName(playerUUID));
    }
}
