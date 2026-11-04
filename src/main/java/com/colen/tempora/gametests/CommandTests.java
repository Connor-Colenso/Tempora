package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.breakBlock;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
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

    // RCon runs commands on its own thread and replies before anything Tempora does on the server thread, so /tempora
    // refuses it. The same command from the console still works, so the refusal isn't down to the command itself.
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

    // /tempora region create over one block of the test, in its dimension.
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

    // A break far from the test, queued on the logger directly.
    private static PlayerBlockBreakEventInfo farBreak(GameTestHelper helper, EntityPlayerMP player) {
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
