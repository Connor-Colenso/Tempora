package com.colen.tempora.loggers.generic;

import java.sql.SQLException;
import java.util.List;

import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.utils.TimeWindow;

/**
 * A logger for events that belong to the whole server rather than a place, such as the weather changing or the
 * server's TPS. Its events have no position, so region rules never apply to them and they are never drawn in the
 * world. They are looked up from anywhere with /tempora global, including the server console.
 * <p>
 * Registered and fed events just like a {@link GenericPositionalLogger}: subclass this and {@link GlobalEventInfo},
 * register it with {@link com.colen.tempora.TemporaLoggerManager#register}, and build each event in your own log
 * methods with {@link #logEvent}.
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericGlobalLogger<EventInfo extends GlobalEventInfo> extends GenericLogger<EventInfo> {

    /**
     * Fills in what every event shares (id and time) and queues it. Each logger's own log methods build their event
     * with this, whether they were triggered by Forge or reported by another mod.
     */
    protected final void logEvent(EventInfo eventInfo) {
        logNewEvent(eventInfo);
    }

    /**
     * Finds the latest events, up to {@code limit} of them, on the query thread (see {@link TemporaQueries}). Showing
     * them says nothing if there are none, so callers checking many loggers can report that once.
     */
    public final FoundEvents<EventInfo> findLatestEvents(TimeWindow window, int limit) {
        String sql = String.format("""
            SELECT * FROM %s
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp DESC
            LIMIT ?;
            """, getLoggerName());

        List<EventInfo> events;
        try {
            events = queryEvents(sql, ps -> {
                long now = System.currentTimeMillis();
                ps.setLong(1, window.oldestMillis(now));
                ps.setLong(2, window.newestMillis(now));
                ps.setInt(3, limit);
            });
        } catch (SQLException e) {
            return FoundEvents.failed(this, e);
        }

        return FoundEvents.found(this, events, limit, TemporaCommand.GLOBAL.command("l:" + getShortName()));
    }
}
