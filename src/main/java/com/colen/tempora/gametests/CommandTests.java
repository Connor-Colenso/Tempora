package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.assertChatWellFormed;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
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

    // A player's events are found wherever they happened, and nobody else's are.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerSearchFindsTheirEventsAnywhere(GameTestHelper helper) {
        GenericLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer alice = fakePlayer(helper, "player_alice");
        FakePlayer bob = fakePlayer(helper, "player_bob");
        String a = PositionalLoggerTests.uuid(alice);
        Probe<FoundEvents> found = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.found");

        helper.startSequence()
            .thenExecute("alice breaks a block here and one far away, bob one here", () -> {
                breakNew(helper, alice, at(1, 1, 1));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(farBreak(helper, alice));
                breakNew(helper, bob, at(2, 1, 1));
            })
            .thenWaitUntil(
                "both of alice's are found",
                WRITE_TICKS,
                () -> LookupTests
                    .assertShown(helper, found.poll(() -> logger.findEventsBy(a, TimeWindow.allTime(), 10, "")), 2))
            .thenSucceed();
    }

    // The command itself: a header naming the player, their events, and the filters it used, all well formed.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerCommandShowsTheirEvents(GameTestHelper helper) {
        FakePlayer carol = fakePlayer(helper, "player_carol");
        String c = PositionalLoggerTests.uuid(carol);
        CapturingSender console = new CapturingSender("playerCommandShowsTheirEvents", helper.getWorld());
        boolean[] asked = { false };

        helper.startSequence()
            .thenExecute("carol breaks a block", () -> breakNew(helper, carol, at(1, 1, 1)))
            .thenWaitUntil("her break is shown", WRITE_TICKS, () -> {
                if (!asked[0]) {
                    asked[0] = true;
                    console.getMessages()
                        .clear();
                    run(console, "player", c, "l:break");
                }
                String text = console.text();
                helper.assertTrue(text.contains("Searched") || text.contains("Nothing found"), "waiting:\n" + text);
                // Not written yet, so ask again next time.
                asked[0] = false;
                helper.assertTrue(text.contains("Player block breaks"), "not written yet:\n" + text);
            })
            .thenExecute("chat is well formed", () -> {
                boolean header = false;
                for (IChatComponent message : console.getMessages()) {
                    assertChatWellFormed(helper, message, "player command");
                    if (message instanceof ChatComponentTranslation translation && translation.getKey()
                        .equals("tempora.command.player.header")) header = true;
                }
                helper.assertTrue(header, "no header, chat was:\n" + console.text());
            })
            .thenSucceed();
    }

    // A logger that doesn't record players is refused by name.
    @GameTest
    public static void playerCommandRefusesLoggerWithoutPlayers(GameTestHelper helper) {
        FakePlayer dave = fakePlayer(helper, "player_dave");
        CapturingSender console = new CapturingSender("playerCommandRefusesLoggerWithoutPlayers", helper.getWorld());

        run(console, "player", PositionalLoggerTests.uuid(dave), "l:spawn");

        helper.assertEquals(
            1,
            console.getMessages()
                .size(),
            "messages, chat was:\n" + console.text());
        assertRedKey(
            helper,
            console.getMessages()
                .get(0),
            "tempora.command.player.no_player");
        helper.succeed();
    }

    // An n: over the server's limit is lowered to it, with a red line saying so. One within it says nothing.
    @GameTest
    public static void countOverLimitIsCapped(GameTestHelper helper) {
        int max = CommandsConfig.maxResultsPerLogger;
        CapturingSender over = new CapturingSender("countOverLimitIsCapped.over", helper.getWorld());
        CapturingSender within = new CapturingSender("countOverLimitIsCapped.within", helper.getWorld());

        run(over, "global", "l:weather", "n:" + (max + 1));
        run(within, "global", "l:weather", "n:" + max);

        helper.assertFalse(
            over.getMessages()
                .isEmpty(),
            "nothing said about n:" + (max + 1));
        IChatComponent capped = over.getMessages()
            .get(0);
        assertRedKey(helper, capped, "tempora.command.count_capped");
        helper.assertEquals(
            String.valueOf(max),
            String.valueOf(((ChatComponentTranslation) capped).getFormatArgs()[1]),
            "capped to");
        assertChatWellFormed(helper, capped, "capped");
        helper.assertTrue(
            within.getMessages()
                .isEmpty(),
            "n:" + max + " should be allowed, chat was:\n" + within.text());
        helper.succeed();
    }

    // A line for every logger, a started one OK with what it holds, then the total.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void statusListsEveryLogger(GameTestHelper helper) {
        CapturingSender console = new CapturingSender("statusListsEveryLogger", helper.getWorld());

        helper.startSequence()
            .thenExecute("ask for the status", () -> run(console, "status"))
            .thenWaitUntil("it is shown", WRITE_TICKS, () -> {
                String text = console.text();
                helper.assertTrue(text.contains("on disk"), "waiting for the status, chat so far:\n" + text);
            })
            .thenExecute("every logger has a well formed line", () -> {
                String text = console.text();
                for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                    helper.assertTrue(
                        text.contains(logger.getShortName() + " ["),
                        logger.getShortName() + " is missing, chat was:\n" + text);
                }
                helper.assertTrue(text.contains("break [OK]"), "break isn't OK, chat was:\n" + text);
                for (IChatComponent message : console.getMessages()) {
                    assertChatWellFormed(helper, message, "status");
                }
            })
            .thenSucceed();
    }

    private static void run(CapturingSender sender, String... args) {
        new TemporaCommand().processCommand(sender, args);
    }

    private static void assertRedKey(GameTestHelper helper, IChatComponent message, String key) {
        helper.assertTrue(
            message instanceof ChatComponentTranslation translation && translation.getKey()
                .equals(key),
            "expected " + key + " but got " + message.getUnformattedText());
        helper.assertEquals(
            EnumChatFormatting.RED,
            message.getChatStyle()
                .getColor(),
            "colour of " + key);
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
