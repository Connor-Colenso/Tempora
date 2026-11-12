package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.command.ICommand;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.AchievementEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.achievement.AchievementEventInfo;
import com.colen.tempora.loggers.command.CommandEventInfo;
import com.colen.tempora.loggers.entity_death.EntityDeathEventInfo;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnEventInfo;
import com.colen.tempora.loggers.explosion.ExplosionEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.item_use.ItemUseEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceEventInfo;
import com.colen.tempora.utils.ChunkPositionUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

// Each positional logger records what it listens for through the same Forge event a real player or mob would fire, and
// the event reads back from the db intact (fields, chat line and render packet). See TestSupport.assertWellFormed
@GameTestHolder(Tempora.MODID)
public class PositionalLoggerTests {

    // How long to wait for the logger's worker to write an event
    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 200;

    private static final TestPos BLOCK = at(2, 1, 2);

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void brokenBlockIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "break");
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
        EntityPlayerMP player = player(helper, "place");
        TestPos below = helper.absolute(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()));
        TestPos pos = helper.absolute(BLOCK);
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "placedBlockIsLogged");

        helper.startSequence()
            .thenExecute("place a block on top of another", () -> {
                helper.setBlock(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()), Blocks.stone);
                ItemStack bricks = new ItemStack(Blocks.brick_block);
                player.inventory.mainInventory[player.inventory.currentItem] = bricks;
                // Through ItemStack like a player's right click is, which is what fires Forge's place event
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

    // Machines asking if they can break or place a block aren't logged as a player doing it. GT miners and pumps ask as
    // fake players, AE2 annihilation planes ask as their owner about the air or liquid in front of them
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void machineProbesAreNotLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> breaks = TemporaEvents.playerBlockBreakLogger;
        GenericPositionalLogger<?> places = TemporaEvents.playerBlockPlaceLogger;
        FakePlayer machine = fakePlayer(helper, "probe_machine");
        EntityPlayerMP owner = player(helper, "probe_owner");
        TestPos asked = helper.absolute(at(2, 1, 2));
        TestPos real = at(2, 1, 4);
        Probe<List<PositionalEventInfo>> realBreak = new Probe<>(helper, "machineProbesAreNotLogged.realBreak");
        Probe<List<PositionalEventInfo>> realPlace = new Probe<>(helper, "machineProbesAreNotLogged.realPlace");
        Probe<List<PositionalEventInfo>> askedBreaks = new Probe<>(helper, "machineProbesAreNotLogged.askedBreaks");
        Probe<List<PositionalEventInfo>> askedPlaces = new Probe<>(helper, "machineProbesAreNotLogged.askedPlaces");

        helper.startSequence()
            .thenExecute("machines ask about blocks, then the owner breaks and places one", () -> {
                helper.setBlock(at(1, 1, 2), Blocks.stone);
                postBreak(helper, machine, at(1, 1, 2), Blocks.stone);
                postPlace(helper, machine, at(1, 1, 2), Blocks.cobblestone);
                postBreak(helper, owner, at(2, 1, 2), Blocks.air);
                postBreak(helper, owner, at(3, 1, 2), Blocks.water);
                // After the asks, so once these are stored the asks would have been too
                helper.setBlock(real, Blocks.stone);
                breakBlock(helper, owner, real);
                helper.setBlock(real, Blocks.brick_block);
                postPlace(helper, owner, real, Blocks.air);
            })
            .thenWaitUntil(
                "the owner's break is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    realBreak.poll(() -> eventsNear(helper, breaks, helper.absolute(real), 0)),
                    1))
            .thenWaitUntil(
                "the owner's place is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    realPlace.poll(() -> eventsNear(helper, places, helper.absolute(real), 0)),
                    1))
            .thenWaitUntil(
                "no break is logged for the asks",
                WRITE_TICKS,
                () -> assertCount(helper, askedBreaks.poll(() -> eventsNear(helper, breaks, asked, 1)), 0))
            .thenWaitUntil(
                "no place is logged for the asks",
                WRITE_TICKS,
                () -> assertCount(helper, askedPlaces.poll(() -> eventsNear(helper, places, asked, 1)), 0))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void explosionIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.explosionLogger;
        TestPos pos = helper.absolute(BLOCK);
        double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "explosionIsLogged");
        Set<ChunkPosition> airBefore = new HashSet<>();

        helper.startSequence()
            .thenExecute("set off an explosion", () -> {
                helper.setBlock(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()), Blocks.dirt);
                World world = helper.getWorld();
                for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
                    if (world.isAirBlock(pos.x() + dx, pos.y() + dy, pos.z() + dz)) {
                        airBefore.add(new ChunkPosition(pos.x() + dx, pos.y() + dy, pos.z() + dz));
                    }
                }
                world.newExplosion(null, x, y, z, 1.5f, false, true);
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

                // Vanilla also reports the air the blast passed through, which wasn't destroyed
                Set<ChunkPosition> destroyed = ChunkPositionUtils.decodePositions(event.affectedBlockCoordinates);
                helper.assertTrue(
                    destroyed.contains(new ChunkPosition(pos.x(), pos.y() - 1, pos.z())),
                    "the dirt is among the destroyed blocks");
                for (ChunkPosition block : destroyed) {
                    helper.assertFalse(airBefore.contains(block), "air counted as destroyed at " + block);
                }
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // Creepers, ghasts and withers with mobGriefing off, and mod blasts set not to break blocks. Still logged but with
    // no destroyed blocks since vanilla leaves them all standing
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void nonDamagingExplosionLogsNoBlocks(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.explosionLogger;
        TestPos pos = helper.absolute(BLOCK);
        double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "nonDamagingExplosionLogsNoBlocks");

        helper.startSequence()
            .thenExecute("set off an explosion that breaks nothing", () -> {
                helper.setBlock(at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()), Blocks.dirt);
                helper.getWorld()
                    .newExplosion(null, x, y, z, 1.5f, false, false);
            })
            .thenWaitUntil(
                "explosion is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    probe.poll(() -> eventsNear(logger, helper.getWorld().provider.dimensionId, x, y, z, 0.01)),
                    1))
            .thenExecute("no blocks are listed as destroyed", () -> {
                helper.assertBlockPresent(Blocks.dirt, at(BLOCK.x(), BLOCK.y() - 1, BLOCK.z()));
                ExplosionEventInfo event = (ExplosionEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals(1.5, event.power, 0.001, "power");
                helper.assertTrue(
                    ChunkPositionUtils.decodePositions(event.affectedBlockCoordinates)
                        .isEmpty(),
                    "no destroyed blocks");
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
                // Straight away, the test server doesn't allow animals and removes them on their first update
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
                helper.assertEquals(uuid(player), event.killerPlayerUUID, "killerPlayerUUID");
                helper.assertEquals("", event.killerMob, "killerMob");
                helper.assertEquals("player", event.damageType, "damageType");
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
                // The event the command handler fires before running a command. Fake players can't run commands
                // themselves, they have no permissions
                ICommand time = MinecraftServer.getServer()
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

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void achievementIsLogged(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.achievementLogger;
        FakePlayer player = fakePlayer(helper, "achievement");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "achievementIsLogged");

        helper.startSequence()
            .thenExecute("a player who has opened their inventory triggers three achievements", () -> {
                // Fake players ignore addStat, so fire the events like addStat would and give the one the player
                // already has straight to their stats
                StatisticsFile stats = player.func_147099_x();
                stats.func_150873_a(player, AchievementList.openInventory, 1);

                // Already had, and one whose parent (Getting Wood) the player doesn't have, neither gets given
                MinecraftForge.EVENT_BUS.post(new AchievementEvent(player, AchievementList.openInventory));
                MinecraftForge.EVENT_BUS.post(new AchievementEvent(player, AchievementList.buildWorkBench));
                // Given. Logged after the other two so if they'd been logged they'd be stored by now too
                MinecraftForge.EVENT_BUS.post(new AchievementEvent(player, AchievementList.mineWood));
            })
            .thenWaitUntil(
                "achievement is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 1))
            .thenExecute("only Getting Wood is logged, and it reads back intact", () -> {
                AchievementEventInfo event = (AchievementEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals(AchievementList.mineWood.statId, event.achievementID, "achievementID");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // Helpers

    // Fires the event a player breaking a block does, then removes the block. Not through
    // ItemInWorldManager.tryHarvestBlock since that sends the player a packet and test players have no connection
    static void breakBlock(GameTestHelper helper, EntityPlayerMP player, TestPos local) {
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

    // Fires the event a player breaking the given block does whatever's there, leaves the world alone
    static void postBreak(GameTestHelper helper, EntityPlayerMP player, TestPos local, Block block) {
        TestPos pos = helper.absolute(local);
        MinecraftForge.EVENT_BUS
            .post(new BlockEvent.BreakEvent(pos.x(), pos.y(), pos.z(), helper.getWorld(), block, 0, player));
    }

    // Fires Forge's place event with the given block as the snapshot, leaves the world alone. The event names the block
    // there now
    static void postPlace(GameTestHelper helper, EntityPlayerMP player, TestPos local, Block snapshot) {
        TestPos pos = helper.absolute(local);
        ForgeEventFactory.onPlayerBlockPlace(
            player,
            new BlockSnapshot(helper.getWorld(), pos.x(), pos.y(), pos.z(), snapshot, 0),
            ForgeDirection.UNKNOWN);
    }

    // Events logged where a fake player stands, for loggers that log at the player
    static List<PositionalEventInfo> eventsAtPlayer(GameTestHelper helper, GenericPositionalLogger<?> logger,
        FakePlayer player) throws Exception {
        return eventsNear(logger, helper.getWorld().provider.dimensionId, player.posX, player.posY, player.posZ, 0.01);
    }

    static void assertCount(GameTestHelper helper, List<?> events, int expected) {
        helper.assertEquals((long) expected, events.size(), "events stored");
    }

    static String uuid(EntityPlayerMP player) {
        return player.getUniqueID()
            .toString();
    }
}
