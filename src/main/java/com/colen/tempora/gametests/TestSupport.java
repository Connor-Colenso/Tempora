package com.colen.tempora.gametests;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** What Tempora's tests share: reading events back, and checks every logged event should pass. */
final class TestSupport {

    private TestSupport() {}

    /**
     * A fake player of its own for one test, standing at the test's origin. Fake players are shared by name, so tests
     * running side by side must not use the same one.
     */
    static FakePlayer fakePlayer(GameTestHelper helper, String testName) {
        return helper.spawnFakePlayer("tempora_" + testName);
    }

    /**
     * Adds a region over blocks of the test, from {@code from} to {@code to} inclusive (test-local), with one logger
     * rule, and removes it once the test ends. Keep it inside the test's own cell, as other tests run beside it.
     */
    static TemporaWorldRegion region(GameTestHelper helper, String label, TestPos from, TestPos to, String loggerName,
        LogRule rule) {
        TestPos min = helper.absolute(from);
        TestPos max = helper.absolute(to);
        return region(
            helper,
            label,
            helper.getWorld().provider.dimensionId,
            Math.min(min.x(), max.x()),
            Math.min(min.y(), max.y()),
            Math.min(min.z(), max.z()),
            Math.max(min.x(), max.x()) + 1,
            Math.max(min.y(), max.y()) + 1,
            Math.max(min.z(), max.z()) + 1,
            loggerName,
            rule);
    }

    /**
     * Adds a region in any dimension, in absolute block coordinates with the max corner exclusive, with one logger
     * rule, and removes it once the test ends.
     */
    static TemporaWorldRegion region(GameTestHelper helper, String label, int dimension, int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ, String loggerName, LogRule rule) {
        TemporaWorldRegion region = new TemporaWorldRegion(dimension, minX, minY, minZ, maxX, maxY, maxZ);
        region.setLabel(label);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setPlayerAuthorUUID(PlayerUtils.UNKNOWN_PLAYER_NAME);
        region.setRegionOriginTimeMs(System.currentTimeMillis());

        TemporaRegionRegistry.add(region);
        TemporaRegionRegistry.setRule(region, loggerName, rule);
        helper.afterTest(() -> TemporaRegionRegistry.remove(region));
        return region;
    }

    // ---------- Reading events back, on the query thread ----------

    /**
     * Every event a positional logger has stored within {@code radius} of a position (a cube, inclusive), oldest
     * first. On the query thread, inside a {@link Probe}.
     */
    static List<PositionalEventInfo> eventsNear(GenericPositionalLogger<?> logger, int dimension, double x, double y,
        double z, double radius) throws SQLException {
        String sql = "SELECT * FROM " + logger.getLoggerName()
            + " WHERE dimensionID = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?"
            + " ORDER BY eventID";

        try (PreparedStatement ps = TemporaQueries.connection(logger)
            .prepareStatement(sql)) {
            ps.setInt(1, dimension);
            ps.setDouble(2, x - radius);
            ps.setDouble(3, x + radius);
            ps.setDouble(4, y - radius);
            ps.setDouble(5, y + radius);
            ps.setDouble(6, z - radius);
            ps.setDouble(7, z + radius);

            List<PositionalEventInfo> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                for (GenericEventInfo event : logger.generateQueryResults(rs)) {
                    events.add((PositionalEventInfo) event);
                }
            }
            // As a lookup does, e.g. the block change logger's stack traces.
            logger.loadExtras(events, TemporaQueries.connection(logger));
            return events;
        }
    }

    // Events stored around a block of the test, from its corner. See eventsNear.
    static List<PositionalEventInfo> eventsNear(GameTestHelper helper, GenericPositionalLogger<?> logger, TestPos pos,
        double radius) throws SQLException {
        return eventsNear(logger, helper.getWorld().provider.dimensionId, pos.x(), pos.y(), pos.z(), radius);
    }

    /** A global logger's events since {@code sinceMillis}, oldest first. On the query thread, inside a Probe. */
    static List<GenericEventInfo> eventsSince(GenericGlobalLogger<?> logger, long sinceMillis) throws SQLException {
        String sql = "SELECT * FROM " + logger.getLoggerName() + " WHERE timestamp >= ? ORDER BY eventID";

        try (PreparedStatement ps = TemporaQueries.connection(logger)
            .prepareStatement(sql)) {
            ps.setLong(1, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return new ArrayList<>(logger.generateQueryResults(rs));
            }
        }
    }

    // ---------- Checks every stored event should pass ----------

    /**
     * Checks events read back from the database: each has an id from the database, unique within its logger, and a
     * positional event's render packet reads back exactly as it was written.
     */
    static void assertWellFormed(GameTestHelper helper, GenericLogger<?> logger,
        List<? extends GenericEventInfo> events) {
        Set<Long> ids = new HashSet<>();
        for (GenericEventInfo event : events) {
            String context = logger.getShortName() + " event " + event.eventID;

            helper.assertTrue(event.eventID > 0, context + " has no id from the database");
            helper.assertTrue(ids.add(event.eventID), context + " has the same id as another event");
            helper.assertTrue(event.timestamp > 0, context + " has no timestamp");

            if (event instanceof PositionalEventInfo positional) {
                assertPacketRoundTrips(helper, positional, context);
            }
        }
    }

    /**
     * Checks the packet that tells a client to draw an event: it reads back what was written, all of it and nothing
     * more, so a field written but not read (or the other way round) is caught here rather than as a garbled event on
     * someone's screen.
     */
    static void assertPacketRoundTrips(GameTestHelper helper, PositionalEventInfo event, String context) {
        ByteBuf written = Unpooled.buffer();
        new RenderEventPacket(event).toBytes(written);
        byte[] bytes = new byte[written.readableBytes()];
        written.getBytes(0, bytes);

        RenderEventPacket read = new RenderEventPacket();
        read.fromBytes(written);
        helper.assertNotNull(read.eventInfo, context + ": render packet could not be read back");
        helper.assertEquals(0L, written.readableBytes(), context + ": render packet has bytes left over once read");

        ByteBuf rewritten = Unpooled.buffer();
        new RenderEventPacket(read.eventInfo).toBytes(rewritten);
        byte[] rewrittenBytes = new byte[rewritten.readableBytes()];
        rewritten.getBytes(0, rewrittenBytes);
        helper.assertTrue(
            Arrays.equals(bytes, rewrittenBytes),
            context + ": render packet changed after being read and written again");
    }
}
