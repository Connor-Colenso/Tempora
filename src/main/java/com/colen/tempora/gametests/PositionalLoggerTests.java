package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.ICommand;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.command.CommandEventInfo;
import com.colen.tempora.loggers.entity_death.EntityDeathEventInfo;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnEventInfo;
import com.colen.tempora.loggers.explosion.ExplosionEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.item_use.ItemUseEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceEventInfo;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * Each positional logger records what it listens for, through the same Forge event a real player or mob would fire,
 * and the event reads back from the database intact: its fields, chat line and render packet. See
 * {@link TestSupport#assertWellFormed}.
 */
@GameTestHolder(Tempora.MODID)
public class PositionalLoggerTests {

    // How long to wait for the logger's worker to write an event.
    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 200;

    private static final TestPos BLOCK = at(2, 1, 2);

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void brokenBlockIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        FakePlayer player = fakePlayer(helper, "break");
        TestPos pos = helper.absolute(BLOCK);
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "brokenBlockIsLogged");

        helper.startSequence()
            .thenExecute("break a block", () -> {
                helper.setBlock(BLOCK, Blocks.stone);
                breakBlock(helper, player, BLOCK);
            })
            .thenWaitUntil(
                "break is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, pos, 0)), 1))
            .thenExecute("event reads back intact", () -> {
                PlayerBlockBreakEventInfo event = (PlayerBlockBreakEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals((long) Block.getIdFromBlock(Blocks.stone), event.blockID, "blockID");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void placedBlockIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockPlaceLogger;
        FakePlayer player = fakePlayer(helper, "place");
        TestPos below = helper.absolute(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()));
        TestPos pos = helper.absolute(BLOCK);
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "placedBlockIsLogged");

        helper.startSequence()
            .thenExecute("place a block on top of another", () -> {
                helper.setBlock(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()), Blocks.stone);
                ItemStack bricks = new ItemStack(Blocks.brick_block);
                player.inventory.mainInventory[player.inventory.currentItem] = bricks;
                // Through ItemStack, as a player's right click is, which is what fires Forge's place event.
                helper.assertTrue(
                    bricks.tryPlaceItemIntoWorld(
                        player,
                        helper.getWorld(),
                        below.x(),
                        below.y(),
                        below.z(),
                        1,
                        0.5f,
                        1f,
                        0.5f),
                    "the block was not placed");
                helper.assertBlockPresent(Blocks.brick_block, BLOCK);
            })
            .thenWaitUntil(
                "place is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, pos, 0)), 1))
            .thenExecute("event reads back intact", () -> {
                PlayerBlockPlaceEventInfo event = (PlayerBlockPlaceEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals((long) Block.getIdFromBlock(Blocks.brick_block), event.blockID, "blockID");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void explosionIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.explosionLogger;
        TestPos pos = helper.absolute(BLOCK);
        double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "explosionIsLogged");

        helper.startSequence()
            .thenExecute("set off an explosion", () -> {
                helper.setBlock(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()), Blocks.dirt);
                helper.getWorld()
                    .newExplosion(null, x, y, z, 1.5f, false, true);
            })
            .thenWaitUntil(
                "explosion is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    probe.poll(() -> eventsNear(logger, helper.getWorld().provider.dimensionId, x, y, z, 0.01)),
                    1))
            .thenExecute("event reads back intact", () -> {
                ExplosionEventInfo event = (ExplosionEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals(1.5, event.power, 0.001, "power");
                helper.assertEquals(PlayerUtils.UNKNOWN_CAUSE, event.exploder, "exploder");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void mobSpawnIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entitySpawnLogger;
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "mobSpawnIsLogged");
        EntityPig[] pig = new EntityPig[1];

        helper.startSequence()
            .thenExecute(
                "spawn a pig",
                () -> pig[0] = helper
                    .spawnEntity(new EntityPig(helper.getWorld()), BLOCK.x() + 0.5, BLOCK.y(), BLOCK.z() + 0.5))
            .thenWaitUntil(
                "spawn is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, helper.absolute(BLOCK), 1)), 1))
            .thenExecute("event reads back intact", () -> {
                EntitySpawnEventInfo event = (EntitySpawnEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals("Pig", event.entityName, "entityName");
                helper.assertEquals(
                    pig[0].getUniqueID()
                        .toString(),
                    event.entityUUID,
                    "entityUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void mobDeathIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entityDeathLogger;
        FakePlayer player = fakePlayer(helper, "death");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "mobDeathIsLogged");

        helper.startSequence()
            .thenExecute("a player kills a pig", () -> {
                EntityPig pig = helper
                    .spawnEntity(new EntityPig(helper.getWorld()), BLOCK.x() + 0.5, BLOCK.y(), BLOCK.z() + 0.5);
                // Straight away: the test server doesn't allow animals, and removes them on their first update.
                pig.attackEntityFrom(DamageSource.causePlayerDamage(player), 1000f);
                helper.assertTrue(pig.isDead || pig.getHealth() <= 0, "the pig did not die");
            })
            .thenWaitUntil(
                "death is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, helper.absolute(BLOCK), 1)), 1))
            .thenExecute("event reads back intact", () -> {
                EntityDeathEventInfo event = (EntityDeathEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals("Pig", event.entityName, "entityName");
                helper.assertEquals(uuid(player), event.killedBy, "killedBy");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void itemUseIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.itemUseLogger;
        FakePlayer player = fakePlayer(helper, "item_use");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "itemUseIsLogged");

        helper.startSequence()
            .thenExecute("right click the air holding a stick", () -> {
                player.inventory.mainInventory[player.inventory.currentItem] = new ItemStack(Items.stick);
                PlayerInteractEvent event = new PlayerInteractEvent(
                    player,
                    PlayerInteractEvent.Action.RIGHT_CLICK_AIR,
                    0,
                    0,
                    0,
                    -1,
                    helper.getWorld());
                MinecraftForge.EVENT_BUS.post(event);
            })
            .thenWaitUntil(
                "item use is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 1))
            .thenExecute("event reads back intact", () -> {
                ItemUseEventInfo event = (ItemUseEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals((long) Item.getIdFromItem(Items.stick), event.itemID, "itemID");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void commandIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.commandLogger;
        FakePlayer player = fakePlayer(helper, "command");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "commandIsLogged");

        helper.startSequence()
            .thenExecute("a player runs /time query daytime", () -> {
                // The event the command handler fires before running a command. Fake players may not run commands
                // themselves, as they have no permissions.
                ICommand time = (ICommand) MinecraftServer.getServer()
                    .getCommandManager()
                    .getCommands()
                    .get("time");
                helper.assertNotNull(time, "no /time command");
                MinecraftForge.EVENT_BUS.post(new CommandEvent(time, player, new String[] { "query", "daytime" }));
            })
            .thenWaitUntil(
                "command is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 1))
            .thenExecute("event reads back intact", () -> {
                CommandEventInfo event = (CommandEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals("time", event.commandName, "commandName");
                helper.assertEquals("query daytime", event.arguments, "arguments");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // ---------- Helpers ----------

    /**
     * Fires the event a player breaking a block does, then removes the block. Not through
     * ItemInWorldManager.tryHarvestBlock, which sends the player a packet, and a fake player has no connection to send
     * it on.
     */
    static void breakBlock(GameTestHelper helper, FakePlayer player, TestPos local) {
        TestPos pos = helper.absolute(local);
        Block block = helper.getWorld()
            .getBlock(pos.x(), pos.y(), pos.z());
        int meta = helper.getWorld()
            .getBlockMetadata(pos.x(), pos.y(), pos.z());
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
            pos.x(),
            pos.y(),
            pos.z(),
            helper.getWorld(),
            block,
            meta,
            player);
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(event), "the block break was cancelled");
        helper.getWorld()
            .setBlockToAir(pos.x(), pos.y(), pos.z());
    }

    // Events logged where a fake player stands, for loggers that log at the player.
    private static List<PositionalEventInfo> eventsAtPlayer(GameTestHelper helper, GenericPositionalLogger<?> logger,
        FakePlayer player) throws Exception {
        return eventsNear(logger, helper.getWorld().provider.dimensionId, player.posX, player.posY, player.posZ, 0.01);
    }

    static void assertCount(GameTestHelper helper, List<?> events, int expected) {
        helper.assertEquals((long) expected, events.size(), "events stored");
    }

    static String uuid(FakePlayer player) {
        return player.getUniqueID()
            .toString();
    }
}
