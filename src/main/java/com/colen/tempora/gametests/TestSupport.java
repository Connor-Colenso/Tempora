package com.colen.tempora.gametests;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;
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
import com.colen.tempora.utils.ResolvedNames;
import com.colen.tempora.utils.TextFilter;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

// Stuff the tests share, reading events back and checks every logged event should pass
final class TestSupport {

    private TestSupport() {}

    // Fake player for one test standing at the test's origin. Fake players are shared by name so tests running side by
    // side can't use the same one
    static FakePlayer fakePlayer(GameTestHelper helper, String testName) {
        return helper.spawnFakePlayer("tempora_" + testName);
    }

    // Non fake player for one test standing at the test's origin, for loggers that skip fake players since those are
    // machines. Never added to the world or player list, no connection
    static EntityPlayerMP player(GameTestHelper helper, String testName) {
        WorldServer world = helper.getWorld();
        String profileName = "tempora_" + testName;
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.UTF_8)),
            profileName);
        EntityPlayerMP player = new EntityPlayerMP(
            MinecraftServer.getServer(),
            world,
            profile,
            new ItemInWorldManager(world));
        TestPos standing = helper.absolute(TestPos.at(0, 1, 0));
        player.setPosition(standing.x() + 0.5, standing.y(), standing.z() + 0.5);
        return player;
    }

    // Adds a region over blocks of the test from from to to inclusive (test local) with one logger rule, removed when
    // the test ends. Keep it inside the test's own cell, other tests run next to it
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

    // Adds a region in any dimension, absolute block coords with the max corner exclusive, with one logger rule,
    // removed when the test ends
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
        TemporaRegionRegistry.setRules(region, Collections.singletonList(loggerName), rule);
        helper.afterTest(() -> TemporaRegionRegistry.remove(region));
        return region;
    }

    // Reading events back on the query thread

    // Every event a positional logger has stored within radius of a position (a cube, inclusive), oldest first. Query
    // thread, inside a Probe
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
            // Same as a lookup, e.g. the block change logger's stack traces
            logger.loadExtras(events, TemporaQueries.connection(logger));
            return events;
        }
    }

    // Events stored around a block of the test, from its corner. See eventsNear
    static List<PositionalEventInfo> eventsNear(GameTestHelper helper, GenericPositionalLogger<?> logger, TestPos pos,
        double radius) throws SQLException {
        return eventsNear(logger, helper.getWorld().provider.dimensionId, pos.x(), pos.y(), pos.z(), radius);
    }

    // A global logger's events since sinceMillis, oldest first. Query thread, inside a Probe
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

    // Checks every stored event should pass

    // Checks events read back from the db. Each has an id from the db unique within its logger, and a positional
    // event's render packet reads back exactly as written
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

    // Checks the packet that tells a client to draw an event reads back what was written, all of it and nothing more.
    // Catches a field written but not read (or the other way round) here instead of as a garbled event on someone's
    // screen
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

    // What each test player's client last answered for some f: text, standing in for NameRequests' round trip so tests
    // about what a filter matches don't wait on it. TextFilterTests.askAndAnswer covers the round trip itself
    private static final Map<String, ResolvedNames> answers = new ConcurrentHashMap<>();

    static void answer(EntityPlayerMP player, String text, ResolvedNames names) {
        answers.put(player.getUniqueID() + "/" + text, names);
    }

    // The answer for that text through the same checks a real one goes through. Throws a CommandException like the
    // command shows if there's none or it named too much
    static TextFilter answered(EntityPlayerMP player, String text) {
        ResolvedNames names = answers.get(player.getUniqueID() + "/" + text);
        if (names == null) throw new CommandException("tempora.command.lookup.name_no_answer", text);
        return TextFilter.of(text, names);
    }
}
