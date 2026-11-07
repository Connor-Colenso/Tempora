package com.colen.tempora.loggers.generic;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.TimeWindow;

/**
 * Logger for events at a place, like a block breaking or a mob dying. Stored with dimension and position, follows
 * region rules and gets looked up around a position (/tempora lookup, the wand). Results go to the player's client
 * which draws them with this logger's renderer from {@link com.colen.tempora.rendering.events.EventRenderers}
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventInfo extends PositionalEventInfo> extends GenericLogger<EventInfo> {

    private boolean logByDefault;

    // Default for the logByDefault config. Override to false for loggers that make too much data to run everywhere, so
    // they only log inside regions that turn them on
    protected boolean defaultForLogByDefault() {
        return true;
    }

    // Whether this logs where no region has a rule for it
    public boolean logsByDefault() {
        return logByDefault;
    }

    // Everywhere unless a region turns it off, or only where a region turns it on
    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(
            settingLine(logByDefault ? "tempora.logger.setting.everywhere" : "tempora.logger.setting.regions_only"));
    }

    // Smallest region here with a rule for this logger decides, otherwise logByDefault does. Full rules in
    // TemporaRegionRegistry
    public final boolean canLogHere(double x, double y, double z, int dimID) {
        TemporaWorldRegion region = TemporaRegionRegistry.findDecidingRegion(getLoggerName(), dimID, x, y, z);
        if (region == null) return logByDefault;

        return region.getRule(getLoggerName()) == LogRule.ON;
    }

    /**
     * Fills in the shared bits (id, time and place) and queues it. Every log method builds its event through this,
     * whether Forge triggered it or another mod reported it through {@link com.colen.tempora.api.TemporaApi}
     */
    protected final void logEvent(EventInfo eventInfo, int dimensionID, double x, double y, double z) {
        eventInfo.dimensionID = dimensionID;
        eventInfo.x = x;
        eventInfo.y = y;
        eventInfo.z = z;

        // A position that isn't a number (some mod's broken entity) can't be stored or found by place. Turned away here
        // since in a batch it'd make the events around it retry one at a time
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            if (!isActive()) return;
            eventInfo.timestamp = System.currentTimeMillis();
            noteDroppedEvent(eventInfo, "its position is not a number");
            return;
        }

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
     * Latest events up to {@code limit} in a cube {@code radius} blocks out from a block, on the query thread (see
     * {@link TemporaQueries}). Radius 0 is just that block. Showing them lists them in chat and draws them, silent if
     * there are none so callers checking lots of loggers can say so once
     */
    public final FoundEvents<EventInfo> findEventsNear(int dimensionId, int centreX, int centreY, int centreZ,
        int radius, TimeWindow window, PlayerFilter players, int limit) {

        // No player column means nothing here can match a player filter
        String playerColumn = playerColumn();
        if (!players.isAnyone() && playerColumn == null) return FoundEvents.none(this);

        // Whole blocks, block n covers n <= x < n + 1 so a mob standing in it (x = n + 0.5) gets found too. The + keeps
        // SQLite off the player index, which would read every event that player has anywhere
        String sql = String.format("""
            SELECT * FROM %s
            WHERE x >= ? AND x < ?
              AND y >= ? AND y < ?
              AND z >= ? AND z < ?
              AND dimensionID = ?
              AND timestamp >= ? AND timestamp <= ?%s
            ORDER BY timestamp DESC
            LIMIT ?;
            """, getLoggerName(), players.isAnyone() ? "" : players.sqlCondition("+" + playerColumn));

        List<EventInfo> events;
        try {
            events = queryEvents(sql, ps -> {
                long now = System.currentTimeMillis();

                ps.setInt(1, centreX - radius);
                ps.setInt(2, centreX + radius + 1);

                ps.setInt(3, centreY - radius);
                ps.setInt(4, centreY + radius + 1);

                ps.setInt(5, centreZ - radius);
                ps.setInt(6, centreZ + radius + 1);

                ps.setInt(7, dimensionId);

                ps.setLong(8, window.oldestMillis(now));
                ps.setLong(9, window.newestMillis(now));

                int next = players.bind(ps, 10);
                ps.setInt(next, limit);
            });
        } catch (SQLException e) {
            return FoundEvents.failed(this, e);
        }

        return FoundEvents.found(this, events, limit, TemporaCommand.LOOKUP.command("l:" + getShortName()));
    }

    // Tells the client what to draw, if anything. Only events in the player's dimension since that's all the client
    // draws, and they might've changed dimension since looking
    @Override
    final void drawInWorld(ICommandSender sender, List<EventInfo> events) {
        if (!(sender instanceof EntityPlayerMP player)) return;

        for (EventInfo eventData : events) {
            if (eventData.dimensionID == player.dimension) RenderEventPacket.sendSafely(eventData, player);
        }
    }
}
