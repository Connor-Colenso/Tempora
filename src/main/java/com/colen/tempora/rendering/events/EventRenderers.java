package com.colen.tempora.rendering.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Which renderer draws each positional logger's events on the client. */
@SideOnly(Side.CLIENT)
public final class EventRenderers {

    // Keyed by logger name, which is what the server sends with each event.
    private static final Map<String, EventRenderer<?>> RENDERERS = new HashMap<>();

    private static final EventRenderer<PositionalEventInfo> FALLBACK = new DefaultEventRenderer();

    private EventRenderers() {}

    /**
     * Sets how a logger's events are drawn. Call this from your client proxy while mods load, after registering the
     * logger itself with {@link TemporaLoggerManager#register}. A logger without a renderer gets a plain box and label.
     */
    public static <E extends PositionalEventInfo> void register(GenericPositionalLogger<E> logger,
        EventRenderer<? super E> renderer) {
        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(renderer, "renderer must not be null");

        String loggerName = logger.getLoggerName();
        if (TemporaLoggerManager.getLogger(loggerName) != logger) {
            throw new IllegalStateException(
                "Register logger " + loggerName + " with TemporaLoggerManager before registering its renderer.");
        }

        if (RENDERERS.putIfAbsent(loggerName, renderer) != null) {
            throw new IllegalStateException("Logger " + loggerName + " already has a renderer.");
        }
    }

    /**
     * The renderer for the named logger, or the plain fallback. Typed by the caller: a logger's renderer only ever gets
     * that logger's events.
     */
    @SuppressWarnings("unchecked")
    static EventRenderer<PositionalEventInfo> get(String loggerName) {
        EventRenderer<?> renderer = RENDERERS.get(loggerName);
        return renderer == null ? FALLBACK : (EventRenderer<PositionalEventInfo>) renderer;
    }
}
