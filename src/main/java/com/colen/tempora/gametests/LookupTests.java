package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
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
                () -> assertShown(helper, wide.poll(() -> logger.findEventsNear(dim, c.x(), c.y(), c.z(), 3, -1)), 2))
            .thenWaitUntil(
                "a radius of 2 finds one",
                WRITE_TICKS,
                () -> assertShown(helper, narrow.poll(() -> logger.findEventsNear(dim, c.x(), c.y(), c.z(), 2, -1)), 1))
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
                    anyTime.poll(() -> logger.findEventsNear(dim, pos.x(), pos.y(), pos.z(), 0, -1)),
                    2))
            .thenWaitUntil(
                "the last day finds one",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    lastDay.poll(() -> logger.findEventsNear(dim, pos.x(), pos.y(), pos.z(), 0, DAY_SECONDS)),
                    1))
            .thenSucceed();
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
                    lookup.poll(() -> logger.findEventsNear(dim, centre.x(), centre.y(), centre.z(), 2, -1))
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
                    lookup.poll(() -> logger.findEventsNear(dim, pos.x(), pos.y(), pos.z(), 0, -1)),
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

    // Shows a lookup's result to a throwaway sender, and checks how many events it showed.
    private static void assertShown(GameTestHelper helper, FoundEvents found, int expected) {
        CapturingSender chat = new CapturingSender("assertShown", helper.getWorld());
        helper.assertEquals((long) expected, found.show(chat), "events shown, chat was:\n" + chat.text());
    }
}
