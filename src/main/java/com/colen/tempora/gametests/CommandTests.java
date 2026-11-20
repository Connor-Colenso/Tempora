package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.answer;
import static com.colen.tempora.gametests.TestSupport.answered;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.ResolvedNames;
import com.colen.tempora.utils.TextFilter;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

// /tempora player, /tempora status and the n: limit, run like the console would through TemporaCommand with a
// CapturingSender
@GameTestHolder(Tempora.MODID)
public class CommandTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;
    // Way outside the test's cell so only a search by player finds it, not by place
    private static final int FAR_AWAY = 20_000;

    // A player's events are found wherever they happened and nobody else's are. Several players, and leaving one out
    // with !, work like p: in a lookup
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerSearchFindsTheirEventsAnywhere(GameTestHelper helper) {
        GenericLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP alice = player(helper, "player_alice");
        EntityPlayerMP bob = player(helper, "player_bob");
        String a = PositionalLoggerTests.uuid(alice);
        String b = PositionalLoggerTests.uuid(bob);
        Probe<FoundEvents<?>> found = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.found");
        Probe<FoundEvents<?>> both = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.both");
        Probe<FoundEvents<?>> notBob = new Probe<>(helper, "playerSearchFindsTheirEventsAnywhere.notBob");

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

    // f: on /tempora player narrows a player's events to what the client named, wherever they happened
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerSearchByText(GameTestHelper helper) {
        GenericLogger<?> breaks = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP alice = player(helper, "player_text_alice");
        EntityPlayerMP bob = player(helper, "player_text_bob");
        PlayerFilter justAlice = PlayerFilter.parse(PositionalLoggerTests.uuid(alice));
        int stone = Block.getIdFromBlock(Blocks.stone);
        Probe<FoundEvents<?>> found = new Probe<>(helper, "playerSearchByText.found");
        Probe<FoundEvents<?>> mobs = new Probe<>(helper, "playerSearchByText.mobs");

        ResolvedNames.Builder stoneNames = new ResolvedNames.Builder();
        stoneNames.item(stone, ResolvedNames.ANY_META);
        answer(alice, "stone", stoneNames.build());
        ResolvedNames.Builder pigNames = new ResolvedNames.Builder();
        pigNames.entity("Pig");
        answer(alice, "pig", pigNames.build());

        helper.startSequence()
            .thenExecute("alice breaks stone and dirt far away, bob breaks stone", () -> {
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(farBreak(helper, alice, Blocks.stone));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(farBreak(helper, alice, Blocks.dirt));
                TemporaEvents.playerBlockBreakLogger.queueEventInfo(farBreak(helper, bob, Blocks.stone));
            })
            .thenWaitUntil("f:stone finds alice's stone only", WRITE_TICKS, () -> {
                FoundEvents<?> result = found
                    .poll(() -> breaks.findEventsBy(justAlice, TimeWindow.allTime(), answered(alice, "stone"), 10, ""));
                LookupTests.assertShown(helper, result, 1);
                PlayerBlockBreakEventInfo event = (PlayerBlockBreakEventInfo) FoundEvents
                    .newestAcross(Collections.singletonList(result), 10)
                    .get(0);
                helper.assertEquals(stone, event.blockID, "block found");
            })
            .thenWaitUntil(
                "a mob filter finds no block breaks",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    mobs.poll(
                        () -> breaks.findEventsBy(justAlice, TimeWindow.allTime(), answered(alice, "pig"), 10, "")),
                    0))
            .thenSucceed();
    }

    // dim: on /tempora player only finds a player's events in that dimension. Global loggers aren't tied to one, so
    // they find nothing with it
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerSearchByDimension(GameTestHelper helper) {
        GenericPositionalLogger<PlayerBlockBreakEventInfo> breaks = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP alice = player(helper, "player_dim_alice");
        PlayerFilter justAlice = PlayerFilter.parse(PositionalLoggerTests.uuid(alice));
        int here = helper.getWorld().provider.dimensionId;
        int nether = here == -1 ? 1 : -1;
        Probe<FoundEvents<?>> anywhere = new Probe<>(helper, "playerSearchByDimension.anywhere");
        Probe<FoundEvents<?>> inHere = new Probe<>(helper, "playerSearchByDimension.here");
        Probe<FoundEvents<?>> inNether = new Probe<>(helper, "playerSearchByDimension.nether");
        Probe<FoundEvents<?>> joins = new Probe<>(helper, "playerSearchByDimension.joins");

        helper.startSequence()
            .thenExecute("alice breaks two blocks here and one in the nether", () -> {
                breaks.queueEventInfo(farBreak(helper, alice, Blocks.stone));
                breaks.queueEventInfo(farBreak(helper, alice, Blocks.dirt));
                PlayerBlockBreakEventInfo inTheNether = farBreak(helper, alice, Blocks.netherrack);
                inTheNether.dimensionID = nether;
                breaks.queueEventInfo(inTheNether);
            })
            .thenWaitUntil(
                "no dim: finds all three",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    anywhere.poll(
                        () -> breaks.findEventsBy(justAlice, TimeWindow.allTime(), TextFilter.any(), null, 10, "")),
                    3))
            .thenWaitUntil(
                "dim: here finds the two here",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    inHere.poll(
                        () -> breaks.findEventsBy(justAlice, TimeWindow.allTime(), TextFilter.any(), here, 10, "")),
                    2))
            .thenWaitUntil(
                "dim: nether finds the one there",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    inNether.poll(
                        () -> breaks.findEventsBy(justAlice, TimeWindow.allTime(), TextFilter.any(), nether, 10, "")),
                    1))
            .thenWaitUntil(
                "a global logger finds nothing with dim:",
                WRITE_TICKS,
                () -> LookupTests.assertShown(
                    helper,
                    joins.poll(
                        () -> TemporaEvents.playerConnectionLogger
                            .findEventsBy(PlayerFilter.anyone(), TimeWindow.allTime(), TextFilter.any(), here, 10, "")),
                    0))
            .thenSucceed();
    }

    // f: needs a client to say what the text names, the console has none so it's refused rather than finding nothing
    @GameTest
    public static void playerTextNeedsAClient(GameTestHelper helper) {
        helper.assertThrows(
            CommandException.class,
            () -> new TemporaCommand()
                .processCommand(MinecraftServer.getServer(), new String[] { "player", "Notch", "f:stone" }),
            "f: from the console was accepted");
        helper.succeed();
    }

    // RCon runs commands on its own thread and replies before anything we do on the server thread, so /tempora refuses
    // it. Same command from the console still works so the refusal isn't the command itself
    @GameTest
    public static void rconIsRefused(GameTestHelper helper) {
        TemporaCommand command = new TemporaCommand();
        String fromConsole = "test_console_region";
        String fromRcon = "test_rcon_region";
        helper.afterTest(() -> {
            for (String label : new String[] { fromConsole, fromRcon }) {
                TemporaWorldRegion region = TemporaRegionRegistry.getByLabel(label);
                if (region != null) TemporaRegionRegistry.remove(region);
            }
        });

        command.processCommand(MinecraftServer.getServer(), regionCreate(helper, fromConsole));
        command.processCommand(RConConsoleSource.instance, regionCreate(helper, fromRcon));

        helper.assertNotNull(TemporaRegionRegistry.getByLabel(fromConsole), "the console should create a region");
        helper.assertNull(TemporaRegionRegistry.getByLabel(fromRcon), "a region was created over RCon");
        helper.succeed();
    }

    // /tempora region create over one block of the test, in its dimension
    private static String[] regionCreate(GameTestHelper helper, String label) {
        TestPos at = helper.absolute(at(1, 1, 1));
        String x = String.valueOf(at.x()), y = String.valueOf(at.y()), z = String.valueOf(at.z());
        return new String[] { "region", "create", label, x, y, z, x, y, z,
            String.valueOf(helper.getWorld().provider.dimensionId) };
    }

    private static void breakNew(GameTestHelper helper, EntityPlayerMP player, TestPos local) {
        helper.setBlock(local, Blocks.stone);
        breakBlock(helper, player, local);
    }

    // A break far from the test, queued on the logger directly
    private static PlayerBlockBreakEventInfo farBreak(GameTestHelper helper, EntityPlayerMP player) {
        return farBreak(helper, player, Blocks.stone);
    }

    private static PlayerBlockBreakEventInfo farBreak(GameTestHelper helper, EntityPlayerMP player, Block block) {
        PlayerBlockBreakEventInfo event = new PlayerBlockBreakEventInfo();
        event.timestamp = System.currentTimeMillis();
        event.dimensionID = helper.getWorld().provider.dimensionId;
        event.x = FAR_AWAY;
        event.y = 64;
        event.z = FAR_AWAY;
        event.blockID = Block.getIdFromBlock(block);
        event.pickBlockID = event.blockID;
        event.playerUUID = PositionalLoggerTests.uuid(player);
        event.encodedNBT = "";
        return event;
    }
}
