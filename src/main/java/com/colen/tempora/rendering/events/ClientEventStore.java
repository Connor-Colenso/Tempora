package com.colen.tempora.rendering.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.colen.tempora.config.ClientConfig;
import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Events the server sent this client to draw, e.g. the results of a lookup. Each is kept for a few seconds, then
 * dropped. Only used on the client thread: the packets that fill it are handled there as well.
 */
@SideOnly(Side.CLIENT)
public final class ClientEventStore {

    public static final long RENDER_DURATION_MILLISECONDS = 10_000;

    // Logger name -> event id -> the event. Keyed by id, so an event sent again (two lookups in a row) is drawn
    // once, and stays for the full time again.
    private static final Map<String, Map<Long, ReceivedEvent>> EVENTS = new LinkedHashMap<>();

    private ClientEventStore() {}

    public static void add(PositionalEventInfo event) {
        EVENTS.computeIfAbsent(event.getLoggerName(), name -> new LinkedHashMap<>())
            .put(event.eventID, new ReceivedEvent(event, System.currentTimeMillis()));
    }

    public static void expire() {
        long cutoff = System.currentTimeMillis() - RENDER_DURATION_MILLISECONDS;

        Iterator<Map<Long, ReceivedEvent>> loggers = EVENTS.values()
            .iterator();
        while (loggers.hasNext()) {
            Map<Long, ReceivedEvent> events = loggers.next();
            events.values()
                .removeIf(received -> received.receivedAt < cutoff);
            if (events.isEmpty()) loggers.remove();
        }
    }

    public static boolean isEmpty() {
        return EVENTS.isEmpty();
    }

    /** Names of the loggers that have events to draw. */
    public static Set<String> loggerNames() {
        return Collections.unmodifiableSet(EVENTS.keySet());
    }

    /**
     * The named logger's events in the context's dimension and within render distance of the camera, farthest first.
     * Typed by the caller: every event the server sends for a logger is made by that logger's factory.
     */
    @SuppressWarnings("unchecked")
    public static <E extends PositionalEventInfo> List<E> visible(String loggerName, EventRenderContext context) {
        Map<Long, ReceivedEvent> events = EVENTS.get(loggerName);
        if (events == null) return Collections.emptyList();

        double maxDistanceSq = (double) ClientConfig.eventRenderDistance * ClientConfig.eventRenderDistance;

        List<E> visible = new ArrayList<>();
        for (ReceivedEvent received : events.values()) {
            PositionalEventInfo event = received.event;
            if (event.dimensionID != context.dimension) continue;
            if (context.distanceSqToCamera(event) > maxDistanceSq) continue;
            visible.add((E) event);
        }

        visible.sort(
            Comparator.comparingDouble((E event) -> context.distanceSqToCamera(event))
                .reversed());
        return visible;
    }

    private static final class ReceivedEvent {

        final PositionalEventInfo event;
        final long receivedAt;

        ReceivedEvent(PositionalEventInfo event, long receivedAt) {
            this.event = event;
            this.receivedAt = receivedAt;
        }
    }
}
