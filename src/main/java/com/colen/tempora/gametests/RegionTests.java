package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.region;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;

import net.minecraft.init.Blocks;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.regions.LogRule;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * Region rules decide where a logger records: the smallest region with a rule for the logger decides, ON wins a tie
 * between regions of the same size, and the logger's logByDefault applies where no region has a say. Uses the block
 * change logger, which is off by default, and the break logger, which is on.
 */
@GameTestHolder(Tempora.MODID)
public class RegionTests {

    private static final String BLOCK_CHANGE = TemporaEvents.BLOCK_CHANGE;
    private static final String BREAK = TemporaEvents.PLAYER_BLOCK_BREAK;

    @GameTest
    public static void loggerDefaultAppliesWithoutRegions(GameTestHelper helper) {
        helper.assertFalse(
            canLog(helper, TemporaEvents.blockChangeLogger, at(2, 1, 2)),
            "the block change logger is off by default");
        helper.assertTrue(
            canLog(helper, TemporaEvents.playerBlockBreakLogger, at(2, 1, 2)),
            "the break logger is on by default");
        helper.succeed();
    }

    @GameTest
    public static void regionTurnsLoggerOnInsideOnly(GameTestHelper helper) {
        region(helper, "test_on_inside_only", at(1, 1, 1), at(2, 2, 2), BLOCK_CHANGE, LogRule.ON);

        helper.assertTrue(canLog(helper, TemporaEvents.blockChangeLogger, at(1, 1, 1)), "inside, min corner");
        helper.assertTrue(canLog(helper, TemporaEvents.blockChangeLogger, at(2, 2, 2)), "inside, max corner");
        helper.assertFalse(canLog(helper, TemporaEvents.blockChangeLogger, at(3, 1, 1)), "just outside");
        helper.assertTrue(
            canLog(helper, TemporaEvents.playerBlockBreakLogger, at(3, 1, 1)),
            "other loggers are unaffected");
        helper.succeed();
    }

    @GameTest
    public static void smallestRegionDecides(GameTestHelper helper) {
        region(helper, "test_smallest_outer", at(0, 1, 0), at(4, 3, 4), BREAK, LogRule.ON);
        region(helper, "test_smallest_inner", at(2, 2, 2), at(2, 2, 2), BREAK, LogRule.OFF);

        helper.assertFalse(
            canLog(helper, TemporaEvents.playerBlockBreakLogger, at(2, 2, 2)),
            "inside the small OFF region");
        helper.assertTrue(
            canLog(helper, TemporaEvents.playerBlockBreakLogger, at(1, 2, 2)),
            "inside the big ON region only");
        helper.succeed();
    }

    @GameTest
    public static void onWinsTieBetweenSameSizeRegions(GameTestHelper helper) {
        region(helper, "test_tie_off", at(1, 1, 1), at(2, 2, 2), BLOCK_CHANGE, LogRule.OFF);
        region(helper, "test_tie_on", at(1, 1, 1), at(2, 2, 2), BLOCK_CHANGE, LogRule.ON);

        helper.assertTrue(canLog(helper, TemporaEvents.blockChangeLogger, at(1, 1, 1)), "ON should win the tie");
        helper.succeed();
    }

    // End to end: with a region turning it on, the block change logger stores a block set inside it, and not one set
    // just outside.
    @GameTest(timeoutTicks = 200)
    public static void blockChangesAreStoredInsideRegionOnly(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.blockChangeLogger;
        TestPos inside = at(1, 1, 1);
        TestPos outside = at(3, 1, 1);
        Probe<List<PositionalEventInfo>> insideEvents = new Probe<>(helper, "blockChangesAreStoredInsideRegionOnly.in");
        Probe<List<PositionalEventInfo>> outsideEvents = new Probe<>(
            helper,
            "blockChangesAreStoredInsideRegionOnly.out");

        helper.startSequence()
            .thenExecute("set a block inside the region and one outside", () -> {
                region(helper, "test_block_change_e2e", at(0, 1, 0), at(2, 2, 2), BLOCK_CHANGE, LogRule.ON);
                helper.setBlock(outside, Blocks.gold_block);
                helper.setBlock(inside, Blocks.gold_block);
            })
            .thenWaitUntil(
                "the change inside is stored",
                100,
                () -> helper.assertTrue(
                    !insideEvents.poll(() -> eventsNear(helper, logger, helper.absolute(inside), 0))
                        .isEmpty(),
                    "no block change stored inside the region"))
            // Region rules are checked when the event is logged, so nothing from outside was ever queued. Once the
            // later change inside is stored, the one outside would have been too.
            .thenWaitUntil(
                "nothing is stored outside",
                100,
                () -> helper.assertEquals(
                    0L,
                    outsideEvents.poll(() -> eventsNear(helper, logger, helper.absolute(outside), 0))
                        .size(),
                    "block changes stored outside the region"))
            .thenSucceed();
    }

    private static boolean canLog(GameTestHelper helper, GenericPositionalLogger<?> logger, TestPos local) {
        TestPos pos = helper.absolute(local);
        return logger.canLogHere(pos.x(), pos.y(), pos.z(), helper.getWorld().provider.dimensionId);
    }
}
