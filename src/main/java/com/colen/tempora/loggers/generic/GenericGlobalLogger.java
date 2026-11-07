package com.colen.tempora.loggers.generic;

import java.sql.SQLException;
import java.util.List;

import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.utils.TimeWindow;

/**
 * Logger for server wide events with no place, like weather changing or TPS. No position so region rules don't apply
 * and nothing gets drawn in the world. Looked up from anywhere with /tempora global, console included
 * <p>
 * Registered and fed the same as a {@link GenericPositionalLogger}. Subclass this and {@link GlobalEventInfo}, register
 * with {@link com.colen.tempora.TemporaLoggerManager#register} and build events in your own log methods with
 * {@link #logEvent}
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericGlobalLogger<EventInfo extends GlobalEventInfo> extends GenericLogger<EventInfo> {

    /**
     * Fills in the shared bits (id and time) and queues it. Every log method builds its event through this, whether
     * Forge triggered it or another mod reported it
     */
    protected final void logEvent(EventInfo eventInfo) {
        logNewEvent(eventInfo);
    }

    /**
     * Latest events up to {@code limit}, on the query thread (see {@link TemporaQueries}). Showing them is silent if
     * there are none so callers checking lots of loggers can say so once
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
