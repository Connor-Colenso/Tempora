package com.colen.tempora.rendering.events;

import java.util.List;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws one positional logger's events in the world on the client, e.g. the results of /tempora lookup, the wand, or
 * /tempora sql. Register one per logger with {@link EventRenderers#register}; a logger without one gets a plain box and
 * label.
 * <p>
 * Renderers draw in world coordinates: the camera offset is already applied. They are handed only the events in the
 * player's dimension within render distance, farthest first, so translucent things blend over what is behind them.
 * Renderers own no events, {@link ClientEventStore} keeps them, so they hold nothing between frames beyond caches.
 */
@SideOnly(Side.CLIENT)
public interface EventRenderer<E extends PositionalEventInfo> {

    /** Draws blocks, entities and lines. */
    default void renderWorld(List<E> events, EventRenderContext context) {}

    /**
     * Draws floating text. Runs after every logger's {@link #renderWorld}, as text is drawn through walls and so would
     * be covered by any block drawn after it.
     */
    default void renderText(List<E> events, EventRenderContext context) {}

    /**
     * Drops whatever this renderer keeps between frames, as the client world unloads (leaving a server, or changing
     * dimension). A cache of entities or tile entities must be emptied here: each keeps the world it was made in, and
     * with it that whole world, in memory.
     */
    default void clearCaches() {}
}
