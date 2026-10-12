package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.NETWORK;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;

/**
 * A logger for events that happen at a place, such as a block breaking or a mob dying. Each event is stored with its
 * dimension and position, follows region rules, and is looked up around a position (/tempora lookup, the wand). Lookup
 * results are sent to the player's client, which draws them with the renderer registered for this logger in
 * {@link com.colen.tempora.rendering.events.EventRenderers}.
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventInfo extends PositionalEventInfo> extends GenericLogger<EventInfo> {

    private static final int MAX_RESULTS = 5;

    private boolean logByDefault;

    // Default value of the logByDefault config option. Override to return false for loggers that would produce too
    // much data to run everywhere, so they only log inside regions that turn them on.
    protected boolean defaultForLogByDefault() {
        return true;
    }

    // Whether this logger records events where no region has a rule for it.
    public boolean logsByDefault() {
        return logByDefault;
    }

    // The smallest region here with a rule for this logger decides, otherwise logByDefault does.
    // See TemporaRegionRegistry for the full rules.
    public final boolean canLogHere(double x, double y, double z, int dimID) {
        TemporaWorldRegion region = TemporaRegionRegistry.findDecidingRegion(getLoggerName(), dimID, x, y, z);
        if (region == null) return logByDefault;

        return region.getRule(getLoggerName()) == LogRule.ON;
    }

    /**
     * Fills in what every event shares (id, time and place) and queues it. Each logger's own log methods build their
     * event with this, whether they were triggered by Forge or reported by another mod through
     * {@link com.colen.tempora.api.TemporaApi}.
     */
    protected final void logEvent(EventInfo eventInfo, int dimensionID, double x, double y, double z) {
        eventInfo.dimensionID = dimensionID;
        eventInfo.x = x;
        eventInfo.y = y;
        eventInfo.z = z;

        logNewEvent(eventInfo);
    }

    @Override
    final boolean shouldRecord(EventInfo eventInfo) {
        return canLogHere(eventInfo.x, eventInfo.y, eventInfo.z, eventInfo.dimensionID);
    }

    @Override
    final void kindConfig(Configuration config) {
        logByDefault = config.getBoolean(
            "logByDefault",
            getLoggerName(),
            defaultForLogByDefault(),
            "Whether this logger records events in places where no region has a rule for it. Use /tempora region set to turn it ON or OFF inside specific regions.");
    }

    @Override
    final Map<String, String> tableIndexes() {
        Map<String, String> indexes = super.tableIndexes();
        indexes.put("xyz_dimension_time", "dimensionID, x, y, z, timestamp");
        return indexes;
    }

    /**
     * Shows the player the latest events within a cube of the given radius around a position, and draws them in the
     * world. A radius of 0 means exactly that block. Negative seconds means no time limit. Says nothing if there are
     * no events, so callers checking many loggers can report that once. Returns how many events were shown.
     */
    public final int showEventsNear(EntityPlayerMP player, int centreX, int centreY, int centreZ, int radius,
        long seconds, int dimensionId) {

        String sql = String.format("""
            SELECT * FROM %s
            WHERE x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
              AND dimensionID = ?
              AND timestamp >= ?
            ORDER BY timestamp DESC
            LIMIT ?;
            """, getLoggerName());

        List<EventInfo> events;
        try {
            events = queryEvents(sql, ps -> {
                // X range
                ps.setInt(1, centreX - radius);
                ps.setInt(2, centreX + radius);

                // Y range
                ps.setInt(3, centreY - radius);
                ps.setInt(4, centreY + radius);

                // Z range
                ps.setInt(5, centreZ - radius);
                ps.setInt(6, centreZ + radius);

                // Dimension ID
                ps.setInt(7, dimensionId);

                // Don't apply any timestamp filter for negative seconds, fetch everything.
                ps.setLong(8, seconds < 0 ? 0 : System.currentTimeMillis() - seconds * 1000L);

                ps.setInt(9, MAX_RESULTS);
            });
        } catch (SQLException e) {
            sendQueryFailed(player, e);
            return 0;
        }

        sendEventsInChat(player, events, MAX_RESULTS, TemporaCommand.LOOKUP.command("l:" + getShortName()));

        // This tells the client what to render in the world, if anything.
        for (EventInfo eventData : events) {
            NETWORK.sendTo(new RenderEventPacket(eventData), player);
        }

        return events.size();
    }
}
