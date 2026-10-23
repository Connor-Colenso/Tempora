package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.ChatComponentWrappedTranslation;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * What /tempora lookup and the wand find: the radius, time and logger filters, and how many events they show. Runs
 * {@link GenericPositionalLogger#findEventsNear} on the query thread and shows the result to a {@link CapturingSender},
 * as the command does for a player.
 */
@GameTestHolder(Tempora.MODID)
public class LookupTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;
    private static final long DAY_SECONDS = TimeUnit.DAYS.toSeconds(1);

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void radiusLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "lookup_radius");
        TestPos centre = at(1, 1, 1);
        TestPos threeAway = at(4, 1, 1);
        TestPos c = helper.absolute(centre);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents> wide = new Probe<>(helper, "radiusLimitsWhatIsFound.wide");
        Probe<FoundEvents> narrow = new Probe<>(helper, "radiusLimitsWhatIsFound.narrow");

        helper.startSequence()
            .thenExecute("break two blocks, three apart", () -> {
                breakNew(helper, player, centre);
                breakNew(helper, player, threeAway);
            })
            .thenWaitUntil(
                "a radius of 3 finds both",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    wide.poll(
                        () -> logger.findEventsNear(
                            dim,
                            c.x(),
                            c.y(),
                            c.z(),
                            3,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    2))
            .thenWaitUntil(
                "a radius of 2 finds one",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    narrow.poll(
                        () -> logger.findEventsNear(
                            dim,
                            c.x(),
                            c.y(),
                            c.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    1))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void timeLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "lookup_time");
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents> anyTime = new Probe<>(helper, "timeLimitsWhatIsFound.anyTime");
        Probe<FoundEvents> lastDay = new Probe<>(helper, "timeLimitsWhatIsFound.lastDay");

        helper.startSequence()
            .thenExecute("a break two days ago, and one now", () -> {
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 2 * DAY_SECONDS));
                breakNew(helper, player, block);
            })
            .thenWaitUntil(
                "no time limit finds both",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    anyTime.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    2))
            .thenWaitUntil(
                "the last day finds one",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    lastDay.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.last(DAY_SECONDS),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    1))
            .thenSucceed();
    }

    // t:3d-1d finds only what happened between 3 days and 1 day ago, whichever way round the range is written.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void timeRangeLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<PlayerBlockBreakEventInfo> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "lookup_range");
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents> lastThreeDays = new Probe<>(helper, "timeRangeLimitsWhatIsFound.lastThreeDays");
        Probe<FoundEvents> range = new Probe<>(helper, "timeRangeLimitsWhatIsFound.range");
        Probe<FoundEvents> reversed = new Probe<>(helper, "timeRangeLimitsWhatIsFound.reversed");

        helper.startSequence()
            .thenExecute("breaks five days ago, two days ago, and now", () -> {
                logger.queueEventInfo(oldBreak(helper, player, pos, 5 * DAY_SECONDS));
                logger.queueEventInfo(oldBreak(helper, player, pos, 2 * DAY_SECONDS));
                breakNew(helper, player, block);
            })
            .thenWaitUntil(
                "the last 3 days finds two",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    lastThreeDays.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.parse("3d"),
                            PlayerFilter.anyone(),
                            n)),
                    2))
            .thenWaitUntil(
                "3d-1d finds the one from two days ago",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    range.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.parse("3d-1d"),
                            PlayerFilter.anyone(),
                            n)),
                    1))
            .thenWaitUntil(
                "1d-3d finds the same",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    reversed.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.parse("1d-3d"),
                            PlayerFilter.anyone(),
                            n)),
                    1))
            .thenSucceed();
    }

    // n: sets how many of the latest events are shown, and the header says "latest" only when there may be more.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void countLimitsWhatIsShown(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "lookup_count");
        TestPos centre = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents> three = new Probe<>(helper, "countLimitsWhatIsShown.three");
        Probe<FoundEvents> ten = new Probe<>(helper, "countLimitsWhatIsShown.ten");
        CapturingSender chat = new CapturingSender("countLimitsWhatIsShown.chat", helper.getWorld());

        helper.startSequence()
            .thenExecute("break seven blocks", () -> {
                for (int x = 0; x < 5; x++) breakNew(helper, player, at(x, 1, 2));
                breakNew(helper, player, at(2, 1, 3));
                breakNew(helper, player, at(2, 1, 1));
            })
            .thenWaitUntil(
                "n:10 shows all seven",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    ten.poll(
                        () -> logger.findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            10)),
                    7))
            .thenWaitUntil("n:3 shows the latest three", WRITE_TICKS, () -> {
                chat.getMessages()
                    .clear();
                helper.assertEquals(
                    3L,
                    three
                        .poll(
                            () -> logger.findEventsNear(
                                dim,
                                centre.x(),
                                centre.y(),
                                centre.z(),
                                2,
                                TimeWindow.allTime(),
                                PlayerFilter.anyone(),
                                3))
                        .show(chat),
                    "events shown");
                helper.assertEquals(
                    "Player block breaks (latest 3) [Info]",
                    chat.getMessages()
                        .get(0)
                        .getUnformattedText(),
                    "header");
            })
            .thenSucceed();
    }

    // p: shows only the named players' events, or with a ! everyone's but theirs, and can list several of either.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerFilterLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer alice = fakePlayer(helper, "lookup_alice");
        FakePlayer bob = fakePlayer(helper, "lookup_bob");
        String a = PositionalLoggerTests.uuid(alice);
        String b = PositionalLoggerTests.uuid(bob);
        TestPos centre = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents> onlyAlice = new Probe<>(helper, "playerFilterLimitsWhatIsFound.onlyAlice");
        Probe<FoundEvents> notAlice = new Probe<>(helper, "playerFilterLimitsWhatIsFound.notAlice");
        Probe<FoundEvents> both = new Probe<>(helper, "playerFilterLimitsWhatIsFound.both");
        Probe<FoundEvents> neither = new Probe<>(helper, "playerFilterLimitsWhatIsFound.neither");

        helper.startSequence()
            .thenExecute("alice breaks two blocks and bob one", () -> {
                breakNew(helper, alice, at(1, 1, 2));
                breakNew(helper, alice, at(2, 1, 2));
                breakNew(helper, bob, at(3, 1, 2));
            })
            .thenWaitUntil(
                "p:alice finds her two",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    onlyAlice.poll(
                        () -> logger.findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.parse(a),
                            n)),
                    2))
            .thenWaitUntil(
                "p:!alice finds bob's one",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    notAlice.poll(
                        () -> logger.findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.parse("!" + a),
                            n)),
                    1))
            .thenWaitUntil(
                "p:alice,bob finds all three",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    both.poll(
                        () -> logger.findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.parse(a + "," + b),
                            n)),
                    3))
            .thenWaitUntil(
                "p:!alice,!bob finds none",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    neither.poll(
                        () -> logger.findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.parse("!" + a + ",!" + b),
                            n)),
                    0))
            .thenExecute("a logger with no player finds nothing when filtering by player", () -> {
                CapturingSender chat = new CapturingSender("playerFilterLimitsWhatIsFound.spawn", helper.getWorld());
                helper.assertEquals(
                    0L,
                    TemporaEvents.entitySpawnLogger
                        .findEventsNear(
                            dim,
                            centre.x(),
                            centre.y(),
                            centre.z(),
                            2,
                            TimeWindow.allTime(),
                            PlayerFilter.parse("!" + a),
                            n)
                        .show(chat),
                    "spawns shown");
            })
            .thenExecute(
                "an unknown player is refused",
                () -> helper.assertThrows(
                    CommandException.class,
                    () -> PlayerFilter.parse("!no_such_player_ever"),
                    "an unknown player was accepted"))
            .thenSucceed();
    }

    // The t: filter's forms, and how "nothing found" messages describe them.
    @GameTest
    public static void timeWindowParsesAndDescribes(GameTestHelper helper) {
        helper.assertEquals("in the last 6h", describe(helper, TimeWindow.parse("6h")), "t:6h");
        helper.assertEquals("between 3d and 1d ago", describe(helper, TimeWindow.parse("3d-1d")), "t:3d-1d");
        helper.assertEquals("between 3d and 1d ago", describe(helper, TimeWindow.parse("1d-3d")), "t:1d-3d");
        helper.assertEquals("at any time", describe(helper, TimeWindow.allTime()), "all time");

        long now = System.currentTimeMillis();
        TimeWindow range = TimeWindow.parse("1d-3d");
        helper.assertEquals(now - 3 * DAY_SECONDS * 1000, range.oldestMillis(now), "oldest of 1d-3d");
        helper.assertEquals(now - DAY_SECONDS * 1000, range.newestMillis(now), "newest of 1d-3d");
        helper.assertEquals(
            Long.MAX_VALUE,
            TimeWindow.parse("6h")
                .newestMillis(now),
            "6h runs up to now");

        // The command system shows the exception's message to the sender.
        for (String bad : new String[] { "3d-", "-1d", "3d-1d-1h", "soon" }) {
            helper.assertThrows(CommandException.class, () -> TimeWindow.parse(bad), "t:" + bad + " was accepted");
        }
        helper.succeed();
    }

    // A lookup shows the newest few events, and its header says there may be more.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void showsLatestFiveWithHeader(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "lookup_latest");
        TestPos centre = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<List<PositionalEventInfo>> stored = new Probe<>(helper, "showsLatestFiveWithHeader.stored");
        Probe<FoundEvents> lookup = new Probe<>(helper, "showsLatestFiveWithHeader.lookup");
        CapturingSender chat = new CapturingSender("showsLatestFiveWithHeader.chat", helper.getWorld());

        helper.startSequence()
            .thenExecute("break six blocks", () -> {
                for (int x = 0; x < 5; x++) breakNew(helper, player, at(x, 1, 2));
                breakNew(helper, player, at(2, 1, 3));
            })
            .thenWaitUntil(
                "all six are stored",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    6L,
                    stored.poll(() -> eventsNear(helper, logger, centre, 2))
                        .size(),
                    "events stored"))
            .thenWaitUntil("the lookup shows five", WRITE_TICKS, () -> {
                chat.getMessages()
                    .clear();
                helper.assertEquals(
                    5L,
                    lookup
                        .poll(
                            () -> logger.findEventsNear(
                                dim,
                                centre.x(),
                                centre.y(),
                                centre.z(),
                                2,
                                TimeWindow.allTime(),
                                PlayerFilter.anyone(),
                                LookupCommand.DEFAULT_COUNT))
                        .show(chat),
                    "events shown");
            })
            .thenExecute("the header names the logger and says these are the latest", () -> {
                IChatComponent header = chat.getMessages()
                    .get(0);
                helper.assertEquals("Player block breaks (latest 5) [Info]", header.getUnformattedText(), "header");
                TestSupport.assertChatWellFormed(helper, header, "header");

                // Chat reaches the client as JSON. The [Info] hover's description must arrive as the component that
                // translates and wraps it there, not as text already translated by the server.
                IChatComponent received = IChatComponent.Serializer
                    .func_150699_a(IChatComponent.Serializer.func_150696_a(header));
                helper.assertEquals(header.getUnformattedText(), received.getUnformattedText(), "header after JSON");
                IChatComponent info = (IChatComponent) ((ChatComponentTranslation) received).getFormatArgs()[2];
                IChatComponent hover = info.getChatStyle()
                    .getChatHoverEvent()
                    .getValue();
                helper.assertInstanceOf(
                    ChatComponentWrappedTranslation.class,
                    hover.getSiblings()
                        .get(0),
                    "[Info] hover description after JSON");

                // The last line, how long events are kept, holds its duration as a translation of its own.
                List<?> lines = hover.getSiblings();
                ChatComponentWrappedTranslation retention = helper.assertInstanceOf(
                    ChatComponentWrappedTranslation.class,
                    lines.get(lines.size() - 1),
                    "[Info] retention line after JSON");
                helper.assertEquals("tempora.logger.setting.retention", retention.getKey(), "retention key");
                helper.assertInstanceOf(
                    ChatComponentWrappedTranslation.class,
                    retention.getArgs()
                        .get(0),
                    "retention duration after JSON");
            })
            .thenSucceed();
    }

    // Lookups name each logger and describe it on hover, so every logger needs both in the lang file.
    @GameTest
    public static void everyLoggerHasNameAndDescription(GameTestHelper helper) {
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            String prefix = "tempora.logger." + logger.getLoggerName();
            helper.assertTrue(StatCollector.canTranslate(prefix + ".name"), "missing " + prefix + ".name");
            helper
                .assertTrue(StatCollector.canTranslate(prefix + ".description"), "missing " + prefix + ".description");
            TestSupport.assertChatWellFormed(helper, logger.getDisplayName(), logger.getLoggerName() + " name");
            TestSupport.assertChatWellFormed(helper, logger.getDescription(), logger.getLoggerName() + " description");
            // The description and a line for each of the server's settings, e.g. how long events are kept.
            TestSupport.assertChatWellFormed(helper, logger.getInfoHover(), logger.getLoggerName() + " [Info]");
        }
        helper.succeed();
    }

    // l: takes short or full names, ignores repeats, and refuses unknown and global loggers with a message.
    @GameTest
    public static void loggerFilterParsesNames(GameTestHelper helper) {
        CapturingSender sender = new CapturingSender("loggerFilterParsesNames", helper.getWorld());

        List<GenericPositionalLogger<?>> two = LookupCommand.parseLoggers(sender, "break,PlayerBlockPlaceLogger,break");
        helper.assertNotNull(two, "break and place were refused: " + sender.text());
        helper.assertEquals(2L, two.size(), "loggers for break,PlayerBlockPlaceLogger,break");
        helper.assertSame(TemporaEvents.playerBlockBreakLogger, two.get(0), "first logger");
        helper.assertSame(TemporaEvents.playerBlockPlaceLogger, two.get(1), "second logger");

        helper.assertEquals(
            (long) com.colen.tempora.TemporaLoggerManager.getPositionalLoggers()
                .size(),
            LookupCommand.parseLoggers(sender, null)
                .size(),
            "no filter means every positional logger");

        int before = sender.getMessages()
            .size();
        helper.assertNull(LookupCommand.parseLoggers(sender, "weather"), "a global logger was accepted");
        helper.assertNull(LookupCommand.parseLoggers(sender, "no_such_logger"), "an unknown logger was accepted");
        helper.assertEquals(
            before + 2L,
            sender.getMessages()
                .size(),
            "each refusal should tell the sender why");
        helper.succeed();
    }

    /**
     * A radius of 0 searches exactly one block: a mob standing in it (x = n + 0.5) is found, and one standing in the
     * next block along is not. Events of mobs and players have positions within a block, not on its corner.
     */
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void radiusZeroFindsMobInBlock(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entitySpawnLogger;
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<List<PositionalEventInfo>> stored = new Probe<>(helper, "radiusZeroFindsMobInBlock.stored");
        Probe<FoundEvents> lookup = new Probe<>(helper, "radiusZeroFindsMobInBlock.lookup");

        helper.startSequence()
            .thenExecute("spawn a pig in the middle of a block, and one in the next block", () -> {
                helper.spawnEntity(new EntityPig(helper.getWorld()), block.x() + 0.5, block.y(), block.z() + 0.5);
                helper.spawnEntity(new EntityPig(helper.getWorld()), block.x() + 1.5, block.y(), block.z() + 0.5);
            })
            .thenWaitUntil(
                "both spawns are stored",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    2L,
                    stored.poll(() -> eventsNear(helper, logger, pos, 2))
                        .size(),
                    "events stored"))
            .thenWaitUntil(
                "a radius 0 lookup at that block finds only the pig in it",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    lookup.poll(
                        () -> logger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    1))
            .thenSucceed();
    }

    // ---------- Helpers ----------

    // Puts a block there and breaks it as a player, logging a break.
    private static void breakNew(GameTestHelper helper, FakePlayer player, TestPos local) {
        helper.setBlock(local, Blocks.stone);
        breakBlock(helper, player, local);
    }

    // A break event from some time ago, to queue on the logger directly as logging always stamps the current time.
    private static PlayerBlockBreakEventInfo oldBreak(GameTestHelper helper, FakePlayer player, TestPos pos,
        long secondsAgo) {
        PlayerBlockBreakEventInfo event = new PlayerBlockBreakEventInfo();
        event.timestamp = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(secondsAgo);
        event.dimensionID = helper.getWorld().provider.dimensionId;
        event.x = pos.x();
        event.y = pos.y();
        event.z = pos.z();
        event.blockID = Block.getIdFromBlock(Blocks.stone);
        event.pickBlockID = event.blockID;
        event.playerUUID = PositionalLoggerTests.uuid(player);
        event.encodedNBT = "";
        return event;
    }

    private static String describe(GameTestHelper helper, TimeWindow window) {
        IChatComponent description = window.describe();
        TestSupport.assertChatWellFormed(helper, description, "time window");
        return description.getUnformattedText();
    }

    // Shows a lookup's result to a throwaway sender, and checks how many events it showed.
    private static void assertShown(GameTestHelper helper, FoundEvents found, int expected) {
        CapturingSender chat = new CapturingSender("assertShown", helper.getWorld());
        helper.assertEquals((long) expected, found.show(chat), "events shown, chat was:\n" + chat.text());
    }
}
