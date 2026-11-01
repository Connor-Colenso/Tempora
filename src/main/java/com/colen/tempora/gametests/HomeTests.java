package com.colen.tempora.gametests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.HomeCommand;
import com.colen.tempora.commands.HomeCommand.ChunkHits;
import com.colen.tempora.commands.HomeCommand.HotSpot;
import com.colen.tempora.loggers.player_position.PlayerPositionEventInfo;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/** What /tempora home finds: the places a player spent the most time, from their logged positions. */
@GameTestHolder(Tempora.MODID)
public class HomeTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;

    // A base over three chunks counts once, a farm and a nether base each count, and a path between them doesn't.
    @GameTest
    public static void picksSeparatePlacesBusiestFirst(GameTestHelper helper) {
        List<ChunkHits> chunks = new ArrayList<>(
            Arrays.asList(
                new ChunkHits(0, 0, 0, 400, 100, 1, 64, 1),
                new ChunkHits(0, 1, 0, 300, 100, 17, 64, 1),
                // Where they stood last in the base, so where its link goes.
                new ChunkHits(0, 0, 1, 200, 300, 5, 70, 20),
                new ChunkHits(0, 20, 20, 500, 200, 330, 12, 330),
                // The same chunk in the nether is a place of its own.
                new ChunkHits(-1, 0, 0, 300, 50, 8, 40, 8)));
        for (int x = 6; x < 16; x++) {
            chunks.add(new ChunkHits(0, x, 10, 1, 150, x * 16, 64, 160));
        }

        List<HotSpot> spots = HomeCommand.pickHotSpots(chunks, 10);
        helper.assertEquals(3, spots.size(), "places found");

        HotSpot base = spots.get(0);
        helper.assertEquals(0, base.dimension, "base dimension");
        helper.assertEquals(900L, base.hits, "time in the base's chunks");
        helper.assertEquals(300L, base.lastSeen, "base last seen");
        helper.assertEquals(5.0, base.x, "base link x");
        helper.assertEquals(70.0, base.y, "base link y");
        helper.assertEquals(20.0, base.z, "base link z");
        helper.assertTrue(Math.abs(base.share - 900.0 / 1710) < 1e-9, "base share was " + base.share);

        HotSpot farm = spots.get(1);
        helper.assertEquals(20, farm.chunkX, "farm chunk x");
        helper.assertEquals(500L, farm.hits, "time at the farm");

        HotSpot nether = spots.get(2);
        helper.assertEquals(-1, nether.dimension, "nether dimension");
        helper.assertEquals(300L, nether.hits, "time in the nether");

        helper.assertEquals(
            2,
            HomeCommand.pickHotSpots(chunks, 2)
                .size(),
            "places found when asked for 2");
        helper.assertTrue(
            HomeCommand.pickHotSpots(new ArrayList<>(), 3)
                .isEmpty(),
            "places found with no positions");
        helper.succeed();
    }

    // Read from the database: chunks round down on the negative side, and t: and dim: limit what is counted.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void findsPlacesFromLoggedPositions(GameTestHelper helper) {
        String uuid = UUID.randomUUID()
            .toString();
        int dim = helper.getWorld().provider.dimensionId;
        long now = System.currentTimeMillis();
        Probe<List<HotSpot>> anyTime = new Probe<>(helper, "findsPlacesFromLoggedPositions.anyTime");
        Probe<List<HotSpot>> lastWeek = new Probe<>(helper, "findsPlacesFromLoggedPositions.lastWeek");
        Probe<List<HotSpot>> otherDim = new Probe<>(helper, "findsPlacesFromLoggedPositions.otherDim");

        helper.startSequence()
            .thenExecute("30 positions now and 10 from two weeks ago, far from other tests", () -> {
                // x -160016.5 is in chunk -10002. Rounding towards zero would make it -160016, in chunk -10001.
                for (int i = 0; i < 30; i++) {
                    TemporaEvents.playerPositionLogger
                        .queueEventInfo(position(uuid, now - i * 1000L, dim, -160016.5, 64, -160000.5));
                }
                for (int i = 0; i < 10; i++) {
                    TemporaEvents.playerPositionLogger.queueEventInfo(
                        position(uuid, now - TimeUnit.DAYS.toMillis(14) - i * 1000L, dim, 160008, 64, 160008));
                }
            })
            .thenWaitUntil("both places are found, busiest first", WRITE_TICKS, () -> {
                List<HotSpot> spots = anyTime.poll(() -> HomeCommand.findHotSpots(uuid, TimeWindow.allTime(), null, 3));
                helper.assertEquals(2, spots.size(), "places found");
                helper.assertEquals(-10002, spots.get(0).chunkX, "busiest chunk x");
                helper.assertEquals(-10001, spots.get(0).chunkZ, "busiest chunk z");
                helper.assertEquals(30L, spots.get(0).hits, "positions in the busiest place");
                helper.assertEquals(now, spots.get(0).lastSeen, "busiest place last seen");
                helper.assertEquals(10000, spots.get(1).chunkX, "second chunk x");
                helper.assertEquals(10L, spots.get(1).hits, "positions in the second place");
            })
            .thenWaitUntil("t:1w leaves out the one from two weeks ago", WRITE_TICKS, () -> {
                List<HotSpot> spots = lastWeek
                    .poll(() -> HomeCommand.findHotSpots(uuid, TimeWindow.parse("1w"), null, 3));
                helper.assertEquals(1, spots.size(), "places found in the last week");
                helper.assertEquals(-10002, spots.get(0).chunkX, "chunk x found in the last week");
            })
            .thenWaitUntil("another dimension has none", WRITE_TICKS, () -> {
                List<HotSpot> spots = otherDim
                    .poll(() -> HomeCommand.findHotSpots(uuid, TimeWindow.allTime(), dim + 1, 3));
                helper.assertTrue(spots.isEmpty(), "places found in another dimension: " + spots.size());
            })
            .thenSucceed();
    }

    private static PlayerPositionEventInfo position(String uuid, long timestamp, int dim, double x, double y,
        double z) {
        PlayerPositionEventInfo event = new PlayerPositionEventInfo();
        event.playerUUID = uuid;
        event.timestamp = timestamp;
        event.dimensionID = dim;
        event.x = x;
        event.y = y;
        event.z = z;
        return event;
    }
}
