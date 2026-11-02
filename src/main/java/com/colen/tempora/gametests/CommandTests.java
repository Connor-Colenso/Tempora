package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * /tempora player, /tempora status and the n: limit, run as the console would, through {@link TemporaCommand} with a
 * {@link CapturingSender}.
 */
@GameTestHolder(Tempora.MODID)
public class CommandTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;
    // Far outside the test's cell, so only a search by player rather than by place finds it.
    private static final int FAR_AWAY = 20_000;

    // A player's events are found wherever they happened, and nobody else's are. Several players, and leaving one out
    // with !, work as p: does in a lookup.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerSearchFindsTheirEventsAnywhere(GameTestHelper helper) {
        GenericLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer alice = fakePlayer(helper, "player_alice");
        FakePlayer bob = fakePlayer(helper, "player_bob");
        String a = PositionalLoggerTests.uuid(alice);
        String b = PositionalLoggerTests.uuid(bob);
        Probe<FoundEvents> found = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.found");
        Probe<FoundEvents> both = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.both");
        Probe<FoundEvents> notBob = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.notBob");

        helper.startSequence()
            .thenExecute("alice breaks a block here and one far away, bob one here", () -> {
                breakNew(helper, alice, at(1, 1, 1));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(farBreak(helper, alice));
                breakNew(helper, bob, at(2, 1, 1));
            })
            .thenWaitUntil(
                "both of alice's are found",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    found.poll(() -> logger.findEventsBy(PlayerFilter.parse(a), TimeWindow.allTime(), 10, "")),
                    2))
            .thenWaitUntil(
                "alice,bob finds all three",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    both.poll(() -> logger.findEventsBy(PlayerFilter.parse(a + "," + b), TimeWindow.allTime(), 10, "")),
                    3))
            .thenWaitUntil(
                "alice,!bob finds alice's two",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    notBob.poll(
                        () -> logger.findEventsBy(PlayerFilter.parse(a + ",!" + b), TimeWindow.allTime(), 10, "")),
                    2))
            .thenSucceed();
    }

    private static void breakNew(GameTestHelper helper, FakePlayer player, TestPos local) {
        helper.setBlock(local, Blocks.stone);
        breakBlock(helper, player, local);
    }

    // A break far from the test, queued on the logger directly.
    private static PlayerBlockBreakEventInfo farBreak(GameTestHelper helper, FakePlayer player) {
        PlayerBlockBreakEventInfo event = new PlayerBlockBreakEventInfo();
        event.timestamp = System.currentTimeMillis();
        event.dimensionID = helper.getWorld().provider.dimensionId;
        event.x = FAR_AWAY;
        event.y = 64;
        event.z = FAR_AWAY;
        event.blockID = Block.getIdFromBlock(Blocks.stone);
        event.pickBlockID = event.blockID;
        event.playerUUID = PositionalLoggerTests.uuid(player);
        event.encodedNBT = "";
        return event;
    }
}
