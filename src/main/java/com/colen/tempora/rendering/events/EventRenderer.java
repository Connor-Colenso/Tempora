package com.colen.tempora.rendering.events;

import java.util.List;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws one positional logger's events in the world on the client, e.g. results of /tempora lookup, the wand or
 * /tempora sql. Register one per logger with {@link EventRenderers#register}, loggers without one get a plain box and
 * label
 * <p>
 * Renderers draw in world coords, the camera offset is already applied. They only get events in the player's dimension
 * within render distance, farthest first so translucent stuff blends over what's behind it. Renderers don't own events,
 * {@link ClientEventStore} keeps them, so they hold nothing between frames except caches
 */
@SideOnly(Side.CLIENT)
public interface EventRenderer<E extends PositionalEventInfo> {

    /** Draws blocks, entities and lines */
    default void renderWorld(List<E> events, EventRenderContext context) {}

    /**
     * Draws floating text. Runs after every logger's {@link #renderWorld} since text is drawn through walls and any
     * block drawn after would cover it
     */
    default void renderText(List<E> events, EventRenderContext context) {}

    /**
     * Drops whatever this renderer keeps between frames when the client world unloads (leaving a server or changing
     * dimension). Caches of entities or TEs have to be cleared here, each one holds its world and so that whole world
     * in memory
     */
    default void clearCaches() {}
}
