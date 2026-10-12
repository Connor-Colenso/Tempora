package com.colen.tempora.loggers.generic;

import java.sql.SQLException;
import java.util.List;

import net.minecraft.command.ICommandSender;

import com.colen.tempora.commands.TemporaCommand;

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
     * Shows the latest events, up to {@code limit} of them. Negative seconds means no time limit. Says nothing if there
     * are no events, so callers checking many loggers can report that once. Returns how many events were shown.
     */
    public final int showLatestEvents(ICommandSender sender, long seconds, int limit) {
        String sql = String.format("""
            SELECT * FROM %s
            WHERE timestamp >= ?
            ORDER BY timestamp DESC
            LIMIT ?;
            """, getLoggerName());

        List<EventInfo> events;
        try {
            events = queryEvents(sql, ps -> {
                ps.setLong(1, seconds < 0 ? 0 : System.currentTimeMillis() - seconds * 1000L);
                ps.setInt(2, limit);
            });
        } catch (SQLException e) {
            sendQueryFailed(sender, e);
            return 0;
        }

        sendEventsInChat(sender, events, limit, TemporaCommand.GLOBAL.command("l:" + getShortName()));
        return events.size();
    }
}
