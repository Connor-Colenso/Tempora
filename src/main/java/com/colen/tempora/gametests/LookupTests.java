package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.answer;
import static com.colen.tempora.gametests.TestSupport.answered;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.commands.command_base.SortOrder;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceEventInfo;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.ResolvedNames;
import com.colen.tempora.utils.TextFilter;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

// What /tempora lookup and the wand find, the radius, time and logger filters and how many events they show. Runs
// GenericPositionalLogger.findEventsNear on the query thread and shows the result to a CapturingSender like the command
// does for a player
@GameTestHolder(Tempora.MODID)
public class LookupTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;
    private static final long DAY_SECONDS = TimeUnit.DAYS.toSeconds(1);

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void radiusLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "lookup_radius");
        TestPos centre = at(1, 1, 1);
        TestPos threeAway = at(4, 1, 1);
        TestPos c = helper.absolute(centre);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> wide = new Probe<>(helper, "radiusLimitsWhatIsFound.wide");
        Probe<FoundEvents<?>> narrow = new Probe<>(helper, "radiusLimitsWhatIsFound.narrow");

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
        EntityPlayerMP player = player(helper, "lookup_time");
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> anyTime = new Probe<>(helper, "timeLimitsWhatIsFound.anyTime");
        Probe<FoundEvents<?>> lastDay = new Probe<>(helper, "timeLimitsWhatIsFound.lastDay");

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
                            TimeWindow.parse("1d"),
                            PlayerFilter.anyone(),
                            LookupCommand.DEFAULT_COUNT)),
                    1))
            .thenSucceed();
    }

    // t:3d-1d only finds what happened between 3 days and 1 day ago, whichever way round the range is written
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void timeRangeLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<PlayerBlockBreakEventInfo> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "lookup_range");
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents<?>> lastThreeDays = new Probe<>(helper, "timeRangeLimitsWhatIsFound.lastThreeDays");
        Probe<FoundEvents<?>> range = new Probe<>(helper, "timeRangeLimitsWhatIsFound.range");
        Probe<FoundEvents<?>> reversed = new Probe<>(helper, "timeRangeLimitsWhatIsFound.reversed");

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

    // n: sets how many of the latest events show, and the header only says "latest" when there might be more
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void countLimitsWhatIsShown(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "lookup_count");
        TestPos centre = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> three = new Probe<>(helper, "countLimitsWhatIsShown.three");
        Probe<FoundEvents<?>> ten = new Probe<>(helper, "countLimitsWhatIsShown.ten");
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
            })
            .thenSucceed();
    }

    // p: shows only the named players' events, or with ! everyone's but theirs, and can list several of either
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerFilterLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP alice = player(helper, "lookup_alice");
        EntityPlayerMP bob = player(helper, "lookup_bob");
        String a = PositionalLoggerTests.uuid(alice);
        String b = PositionalLoggerTests.uuid(bob);
        TestPos centre = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents<?>> onlyAlice = new Probe<>(helper, "playerFilterLimitsWhatIsFound.onlyAlice");
        Probe<FoundEvents<?>> notAlice = new Probe<>(helper, "playerFilterLimitsWhatIsFound.notAlice");
        Probe<FoundEvents<?>> both = new Probe<>(helper, "playerFilterLimitsWhatIsFound.both");
        Probe<FoundEvents<?>> neither = new Probe<>(helper, "playerFilterLimitsWhatIsFound.neither");

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

    // s:time takes the newest n of every logger's events together, not the newest n of each
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void timeSortTakesNewestAcrossLoggers(GameTestHelper helper) {
        EntityPlayerMP player = player(helper, "lookup_by_time");
        TestPos pos = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        long now = System.currentTimeMillis();
        Probe<List<FoundEvents<?>>> found = new Probe<>(helper, "timeSortTakesNewestAcrossLoggers.found");

        helper.startSequence()
            .thenExecute("breaks 4 and 2 days ago, places 3 and 1 day ago, at the same block", () -> {
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 4 * DAY_SECONDS));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 2 * DAY_SECONDS));
                TemporaEvents.playerBlockPlaceLogger.queueEventInfo(oldPlace(helper, player, pos, 3 * DAY_SECONDS));
                TemporaEvents.playerBlockPlaceLogger.queueEventInfo(oldPlace(helper, player, pos, DAY_SECONDS));
            })
            .thenWaitUntil("both loggers find their two", WRITE_TICKS, () -> {
                List<FoundEvents<?>> both = found.poll(
                    () -> Arrays.asList(
                        TemporaEvents.playerBlockBreakLogger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            3),
                        TemporaEvents.playerBlockPlaceLogger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            0,
                            TimeWindow.allTime(),
                            PlayerFilter.anyone(),
                            3)));
                helper.assertEquals(
                    4L,
                    (long) FoundEvents.newestAcross(both, 10)
                        .size(),
                    "events found");
            })
            .thenExecute("n:3 by time takes the place, break and place from 1, 2 and 3 days ago", () -> {
                List<FoundEvents<?>> both = found.latest();
                List<GenericEventInfo> newest = FoundEvents.newestAcross(both, 3);
                helper.assertEquals(3L, (long) newest.size(), "events taken");
                long[] daysAgo = { 1, 2, 3 };
                for (int i = 0; i < daysAgo.length; i++) {
                    helper.assertEquals(
                        daysAgo[i],
                        Math.round((now - newest.get(i).timestamp) / (double) TimeUnit.DAYS.toMillis(1)),
                        "days ago of event " + i + ", newest first");
                }
                helper.assertTrue(
                    newest.get(0) instanceof PlayerBlockPlaceEventInfo
                        && newest.get(1) instanceof PlayerBlockBreakEventInfo
                        && newest.get(2) instanceof PlayerBlockPlaceEventInfo,
                    "a place, a break, then a place");

                CapturingSender chat = new CapturingSender("timeSortTakesNewestAcrossLoggers.chat", helper.getWorld());
                helper.assertEquals(
                    3L,
                    (long) LookupCommand.show(chat, both, 3, SortOrder.TIME),
                    "shown by time, chat was:\n" + chat.text());
                helper.assertEquals(
                    4L,
                    (long) LookupCommand.show(chat, both, 3, SortOrder.LOGGER),
                    "shown by logger, chat was:\n" + chat.text());
            })
            .thenSucceed();
    }

    // s: takes logger or time in any case and refuses anything else with a message
    @GameTest
    public static void sortFilterParses(GameTestHelper helper) {
        CapturingSender sender = new CapturingSender("sortFilterParses", helper.getWorld());
        helper.assertSame(SortOrder.LOGGER, SortOrder.parse(sender, "logger"), "s:logger");
        helper.assertSame(SortOrder.TIME, SortOrder.parse(sender, "Time"), "s:Time");

        int before = sender.getMessages()
            .size();
        helper.assertNull(SortOrder.parse(sender, "distance"), "s:distance was accepted");
        helper.assertEquals(
            before + 1L,
            sender.getMessages()
                .size(),
            "the refusal should tell the sender why");
        helper.succeed();
    }

    // t: filter forms, range either way round
    @GameTest
    public static void timeWindowParses(GameTestHelper helper) {
        long now = System.currentTimeMillis();
        TimeWindow range = TimeWindow.parse("1d-3d");
        helper.assertEquals(now - 3 * DAY_SECONDS * 1000, range.oldestMillis(now), "oldest of 1d-3d");
        helper.assertEquals(now - DAY_SECONDS * 1000, range.newestMillis(now), "newest of 1d-3d");
        helper.assertEquals(
            Long.MAX_VALUE,
            TimeWindow.parse("6h")
                .newestMillis(now),
            "6h runs up to now");

        // The command system shows the exception's message to the sender
        for (String bad : new String[] { "3d-", "-1d", "3d-1d-1h", "soon" }) {
            helper.assertThrows(CommandException.class, () -> TimeWindow.parse(bad), "t:" + bad + " was accepted");
        }
        helper.succeed();
    }

    // l: takes short or full names, ignores repeats and refuses unknown and global loggers with a message
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

    // Radius 0 searches exactly one block. A mob standing in it (x = n + 0.5) is found and one in the next block along
    // isn't. Mob and player events have positions inside a block, not on its corner
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void radiusZeroFindsMobInBlock(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entitySpawnLogger;
        TestPos block = at(2, 1, 2);
        TestPos pos = helper.absolute(block);
        int dim = helper.getWorld().provider.dimensionId;
        Probe<List<PositionalEventInfo>> stored = new Probe<>(helper, "radiusZeroFindsMobInBlock.stored");
        Probe<FoundEvents<?>> lookup = new Probe<>(helper, "radiusZeroFindsMobInBlock.lookup");

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

    // Helpers

    // Puts a block there and breaks it as a player, logging a break
    private static void breakNew(GameTestHelper helper, EntityPlayerMP player, TestPos local) {
        helper.setBlock(local, Blocks.stone);
        breakBlock(helper, player, local);
    }

    // A break event from a while ago, queued on the logger directly since logging always stamps the current time
    private static PlayerBlockBreakEventInfo oldBreak(GameTestHelper helper, EntityPlayerMP player, TestPos pos,
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

    // A place event from a while ago, same as oldBreak
    private static PlayerBlockPlaceEventInfo oldPlace(GameTestHelper helper, EntityPlayerMP player, TestPos pos,
        long secondsAgo) {
        PlayerBlockPlaceEventInfo event = new PlayerBlockPlaceEventInfo();
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

    // f: finds what the client matched the text to, any meta or exact meta
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void nameFilterLimitsWhatIsFound(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "lookup_name");
        TestPos pos = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        int wool = Block.getIdFromBlock(Blocks.wool);
        int stoneID = Block.getIdFromBlock(Blocks.stone);
        int dirtID = Block.getIdFromBlock(Blocks.dirt);
        Probe<FoundEvents<?>> all = new Probe<>(helper, "nameFilterLimitsWhatIsFound.all");
        Probe<FoundEvents<?>> stone = new Probe<>(helper, "nameFilterLimitsWhatIsFound.stone");
        Probe<FoundEvents<?>> stoneOrDirt = new Probe<>(helper, "nameFilterLimitsWhatIsFound.stoneOrDirt");
        Probe<FoundEvents<?>> redWool = new Probe<>(helper, "nameFilterLimitsWhatIsFound.redWool");

        helper.startSequence()
            .thenExecute("break stone, dirt, white wool and red wool", () -> {
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 60, Blocks.stone, 0));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 60, Blocks.dirt, 0));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 60, Blocks.wool, 0));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(oldBreak(helper, player, pos, 60, Blocks.wool, 14));
            })
            .thenWaitUntil(
                "all 4 are found without f:",
                WRITE_TICKS,
                () -> assertShown(helper, all.poll(() -> near(logger, dim, pos, TextFilter.any())), 4))
            .thenWaitUntil(
                "f:stone (any meta of stone) finds the stone",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    stone.poll(() -> near(logger, dim, pos, sent(player, "stone", stoneID, ResolvedNames.ANY_META))),
                    1))
            .thenWaitUntil(
                "f:stone,dirt finds both",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    stoneOrDirt.poll(
                        () -> near(
                            logger,
                            dim,
                            pos,
                            sent(
                                player,
                                "stone,dirt",
                                stoneID,
                                ResolvedNames.ANY_META,
                                dirtID,
                                ResolvedNames.ANY_META))),
                    2))
            .thenWaitUntil(
                "f:red_wool (wool:14) finds only the red wool",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    redWool.poll(() -> near(logger, dim, pos, sent(player, "red_wool", wool, 14))),
                    1))
            .thenExecute("f: naming nothing skips breaks, and blocks don't apply to mob loggers", () -> {
                helper.assertTrue(
                    logger.textFilterCondition(sent(player, "nothing_is_called_this")) == null,
                    "text naming no block still searched breaks");
                helper.assertTrue(
                    TemporaEvents.entityDeathLogger
                        .textFilterCondition(sent(player, "stone", stoneID, ResolvedNames.ANY_META)) == null,
                    "a block filter applied to mob deaths");
            })
            .thenSucceed();
    }

    // A filter as the client would send it for text, id and meta pairs, then the command's view of it
    private static TextFilter sent(EntityPlayerMP player, String text, int... idsAndMetas) {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        for (int i = 0; i < idsAndMetas.length; i += 2) builder.item(idsAndMetas[i], idsAndMetas[i + 1]);
        answer(player, text, builder.build());
        return answered(player, text);
    }

    private static FoundEvents<?> near(GenericPositionalLogger<?> logger, int dim, TestPos pos, TextFilter names) {
        return logger
            .findEventsNear(dim, pos.x(), pos.y(), pos.z(), 0, TimeWindow.allTime(), PlayerFilter.anyone(), names, 10);
    }

    private static PlayerBlockBreakEventInfo oldBreak(GameTestHelper helper, EntityPlayerMP player, TestPos pos,
        long secondsAgo, Block block, int meta) {
        PlayerBlockBreakEventInfo event = oldBreak(helper, player, pos, secondsAgo);
        event.blockID = Block.getIdFromBlock(block);
        event.metadata = meta;
        event.pickBlockID = event.blockID;
        event.pickBlockMeta = meta;
        return event;
    }

    // Shows a lookup's result to a throwaway sender and checks how many events it showed
    static void assertShown(GameTestHelper helper, FoundEvents<?> found, int expected) {
        CapturingSender chat = new CapturingSender("assertShown", helper.getWorld());
        helper.assertEquals((long) expected, found.show(chat), "events shown, chat was:\n" + chat.text());
    }
}
