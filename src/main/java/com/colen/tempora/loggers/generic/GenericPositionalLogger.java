package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.RenderingUtils.squaredDistance;

import java.awt.Color;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A logger for events that happen at a place, such as a block breaking or a mob dying. Each event is stored with its
 * dimension and position, follows region rules, is looked up around a position (/tempora lookup, the wand), and can be
 * drawn in the world on the client.
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventInfo extends PositionalEventInfo> extends GenericLogger<EventInfo> {

    protected List<EventInfo> transparentEventsToRenderInWorld = new ArrayList<>();
    protected List<EventInfo> nonTransparentEventsToRenderInWorld = new ArrayList<>();
    private static final long SECONDS_RENDERING_DURATION = 10;

    private static final int MAX_RESULTS = 5;

    private boolean logByDefault;

    public void addEventToRender(EventInfo event) {
        event.eventRenderCreationTime = System.currentTimeMillis();

        if (event.needsTransparencyToRender()) {
            transparentEventsToRenderInWorld.add(event);
        } else {
            nonTransparentEventsToRenderInWorld.add(event);
        }
    }

    @SideOnly(Side.CLIENT)
    public List<EventInfo> getSortedLatestEventsByDistance(Collection<EventInfo> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, EventInfo> latestPerBlock = new HashMap<>();

        for (EventInfo element : input) {
            if (element.dimensionID != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;
            EventInfo existing = latestPerBlock.get(key);

            if (existing == null || element.timestamp > existing.timestamp) {
                latestPerBlock.put(key, element);
            }
        }

        List<EventInfo> sorted = new ArrayList<>(latestPerBlock.values());
        sortByDistanceDescending(sorted, e);
        return sorted;
    }

    @SideOnly(Side.CLIENT)
    public void sortByDistanceDescending(List<EventInfo> list, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        list.sort((a, b) -> Double.compare(squaredDistance(b, px, py, pz), squaredDistance(a, px, py, pz)));
    }

    // Default value of the logByDefault config option. Override to return false for loggers that would produce too
    // much data to run everywhere, so they only log inside regions that turn them on.
    protected boolean defaultForLogByDefault() {
        return true;
    }

    // Whether this logger records events where no region has a rule for it.
    public boolean logsByDefault() {
        return logByDefault;
    }

    /** Draws this logger's recent lookup results in the world. Loggers with nothing to draw can leave this alone. */
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {}

    /**
     * Draws floating text for this logger's recent lookup results. Runs after every logger's
     * {@link #renderEventsInWorld}, as text is drawn through walls and so would be covered by any block drawn after it.
     */
    @SideOnly(Side.CLIENT)
    public void renderEventTextInWorld(RenderWorldLastEvent renderEvent) {}

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
        indexes.put("xyz_dimension_time", "dimensionID, x, y, z, timestamp DESC");
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

        sendEventsInChat(player, events);

        // This tells the client what to render in the world, if anything.
        for (EventInfo eventData : events) {
            NETWORK.sendTo(new RenderEventPacket(eventData), player);
        }

        return events.size();
    }

    public void clearOldEventsToRender() {
        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        transparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
        nonTransparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
    }

    public Color getColour() {
        return Color.RED;
    }
}
