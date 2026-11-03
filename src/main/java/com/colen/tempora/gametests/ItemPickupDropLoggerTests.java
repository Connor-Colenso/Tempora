package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.LookupTests.assertShown;
import static com.colen.tempora.gametests.PositionalLoggerTests.assertCount;
import static com.colen.tempora.gametests.PositionalLoggerTests.eventsAtPlayer;
import static com.colen.tempora.gametests.PositionalLoggerTests.uuid;
import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropEventInfo;
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropLogger;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.TimeWindow;
import com.colen.tempora.utils.nbt.NBTUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import cpw.mods.fml.common.eventhandler.EventBus;

/**
 * The item pickup and drop logger, through the vanilla code a player picking up or throwing out an item runs, and the
 * event Forge fires for a player's death drops.
 */
@GameTestHolder(Tempora.MODID)
public class ItemPickupDropLoggerTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 200;

    // A whole stack, then only what fits of another: 3 of the 5 on the ground, the rest stays there. Vanilla skips
    // FML's pickup event for the second, though the items still move.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void pickedUpItemsAreLogged(GameTestHelper helper) {
        ItemPickupDropLogger logger = TemporaEvents.itemPickupDropLogger;
        FakePlayer player = fakePlayer(helper, "pickup");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "pickedUpItemsAreLogged");

        helper.startSequence()
            .thenExecute("pick up 2 sticks, then 5 cobblestone with room for 3", () -> {
                // Slot 1 is the only empty one. Not the last slot: Backhand adds an offhand slot to the end of the
                // main inventory, which pickups skip.
                ItemStack[] slots = player.inventory.mainInventory;
                slots[0] = new ItemStack(Blocks.cobblestone, 61);
                slots[1] = null;
                for (int i = 2; i < slots.length; i++) slots[i] = new ItemStack(Blocks.dirt, 64);

                EntityItem sticks = itemOnGround(helper, player, new ItemStack(Items.stick, 2));
                EntityItem cobblestone = itemOnGround(helper, player, new ItemStack(Blocks.cobblestone, 5));
                // What a player walking into an item does each tick.
                sticks.onCollideWithPlayer(player);
                cobblestone.onCollideWithPlayer(player);

                helper.assertTrue(sticks.isDead, "the sticks were not all picked up");
                helper.assertEquals(64L, slots[0].stackSize, "cobblestone in the player's inventory");
                helper.assertEquals(2L, cobblestone.getEntityItem().stackSize, "cobblestone left on the ground");
            })
            .thenWaitUntil(
                "both pickups are logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 2))
            .thenExecute("events read back intact", () -> {
                ItemPickupDropEventInfo sticks = (ItemPickupDropEventInfo) probe.latest()
                    .get(0);
                ItemPickupDropEventInfo cobblestone = (ItemPickupDropEventInfo) probe.latest()
                    .get(1);
                long pickup = ItemPickupDropLogger.Action.PICKUP.getDbId();

                helper.assertEquals(pickup, sticks.action, "sticks action");
                helper.assertEquals((long) Item.getIdFromItem(Items.stick), sticks.itemID, "sticks itemID");
                helper.assertEquals(2L, sticks.amount, "sticks amount");

                helper.assertEquals(pickup, cobblestone.action, "cobblestone action");
                helper.assertEquals((long) Block.getIdFromBlock(Blocks.cobblestone), cobblestone.itemID, "itemID");
                helper.assertEquals(3L, cobblestone.amount, "cobblestone amount");
                helper.assertEquals(NBTUtils.NO_NBT, cobblestone.encodedNBT, "encodedNBT");
                helper.assertEquals(uuid(player), cobblestone.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // Throwing out the whole held stack, as Ctrl+Q does, with its NBT and the way the player looked.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void droppedItemsAreLogged(GameTestHelper helper) {
        ItemPickupDropLogger logger = TemporaEvents.itemPickupDropLogger;
        FakePlayer player = fakePlayer(helper, "drop");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "droppedItemsAreLogged");

        helper.startSequence()
            .thenExecute("throw out 10 named sticks, looking up and to the north west", () -> {
                player.rotationYaw = 135f;
                player.rotationPitch = -20f;

                ItemStack sticks = new ItemStack(Items.stick, 10, 0);
                sticks.setStackDisplayName("Evidence");
                player.inventory.mainInventory[player.inventory.currentItem] = sticks;

                EntityItem thrown = player.dropOneItem(true);
                helper.assertNotNull(thrown, "nothing was thrown");
                helper.afterTest(thrown::setDead);
            })
            .thenWaitUntil(
                "drop is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 1))
            .thenExecute("event reads back intact", () -> {
                ItemPickupDropEventInfo event = (ItemPickupDropEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals((long) ItemPickupDropLogger.Action.DROP.getDbId(), event.action, "action");
                helper.assertEquals((long) Item.getIdFromItem(Items.stick), event.itemID, "itemID");
                helper.assertEquals(10L, event.amount, "amount");
                helper.assertEquals(135.0, event.rotationYaw, 0.001, "rotationYaw");
                helper.assertEquals(-20.0, event.rotationPitch, 0.001, "rotationPitch");
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");

                NBTTagCompound tag = NBTUtils.decodeItemNBT(event.encodedNBT);
                helper.assertNotNull(tag, "the sticks' NBT was not logged");
                helper.assertEquals(
                    "Evidence",
                    tag.getCompoundTag("display")
                        .getString("Name"),
                    "name in the logged NBT");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // Death drops are logged once per kind of item, however many stacks of it there were.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void deathDropsAreLogged(GameTestHelper helper) {
        ItemPickupDropLogger logger = TemporaEvents.itemPickupDropLogger;
        FakePlayer player = fakePlayer(helper, "death_drop");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "deathDropsAreLogged");

        helper.startSequence()
            .thenExecute("die holding two stacks of cobblestone and a diamond", () -> {
                // The event a dying player fires, as fake players cannot die. Posted on a bus of the logger's own, as
                // other mods' handlers expect a logged-in player (Galacticraft's NPEs) or act on the death (graves).
                ArrayList<EntityItem> drops = new ArrayList<>();
                drops.add(dropAtPlayer(helper, player, new ItemStack(Blocks.cobblestone, 64)));
                drops.add(dropAtPlayer(helper, player, new ItemStack(Items.diamond, 1)));
                drops.add(dropAtPlayer(helper, player, new ItemStack(Blocks.cobblestone, 10)));
                EventBus bus = new EventBus();
                bus.register(logger);
                helper.assertFalse(
                    bus.post(new PlayerDropsEvent(player, DamageSource.outOfWorld, drops, false)),
                    "the drops were cancelled");
            })
            .thenWaitUntil(
                "both kinds of item are logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsAtPlayer(helper, logger, player)), 2))
            .thenExecute("events read back intact", () -> {
                ItemPickupDropEventInfo cobblestone = (ItemPickupDropEventInfo) probe.latest()
                    .get(0);
                ItemPickupDropEventInfo diamond = (ItemPickupDropEventInfo) probe.latest()
                    .get(1);
                long deathDrop = ItemPickupDropLogger.Action.DEATH_DROP.getDbId();

                helper.assertEquals(deathDrop, cobblestone.action, "cobblestone action");
                helper.assertEquals((long) Block.getIdFromBlock(Blocks.cobblestone), cobblestone.itemID, "itemID");
                helper.assertEquals(74L, cobblestone.amount, "cobblestone amount");

                helper.assertEquals(deathDrop, diamond.action, "diamond action");
                helper.assertEquals((long) Item.getIdFromItem(Items.diamond), diamond.itemID, "itemID");
                helper.assertEquals(1L, diamond.amount, "diamond amount");

                helper.assertEquals(uuid(player), cobblestone.playerUUID, "playerUUID");
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // p: on a lookup finds only the named players' pickups and drops.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerFilterFindsTheirItems(GameTestHelper helper) {
        ItemPickupDropLogger logger = TemporaEvents.itemPickupDropLogger;
        FakePlayer alice = fakePlayer(helper, "drop_alice");
        FakePlayer bob = fakePlayer(helper, "drop_bob");
        String a = uuid(alice);
        String b = uuid(bob);
        int dim = helper.getWorld().provider.dimensionId;
        int x = (int) Math.floor(alice.posX), y = (int) Math.floor(alice.posY), z = (int) Math.floor(alice.posZ);
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents<?>> onlyAlice = new Probe<>(helper, "playerFilterFindsTheirItems.onlyAlice");
        Probe<FoundEvents<?>> both = new Probe<>(helper, "playerFilterFindsTheirItems.both");

        helper.startSequence()
            .thenExecute("alice and bob each throw out a stick", () -> {
                for (FakePlayer player : new FakePlayer[] { alice, bob }) {
                    player.inventory.mainInventory[player.inventory.currentItem] = new ItemStack(Items.stick);
                    EntityItem thrown = player.dropOneItem(false);
                    helper.assertNotNull(thrown, "nothing was thrown");
                    helper.afterTest(thrown::setDead);
                }
            })
            .thenWaitUntil(
                "l:pickup p:alice finds hers",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    onlyAlice.poll(
                        () -> logger.findEventsNear(dim, x, y, z, 1, TimeWindow.allTime(), PlayerFilter.parse(a), n)),
                    1))
            .thenWaitUntil(
                "l:pickup p:alice,bob finds both",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    both.poll(
                        () -> logger
                            .findEventsNear(dim, x, y, z, 1, TimeWindow.allTime(), PlayerFilter.parse(a + "," + b), n)),
                    2))
            .thenSucceed();
    }

    // An item at the player's feet that can be picked up straight away. Not spawned, so nothing else can touch it.
    private static EntityItem itemOnGround(GameTestHelper helper, FakePlayer player, ItemStack stack) {
        EntityItem item = dropAtPlayer(helper, player, stack);
        item.delayBeforeCanPickup = 0;
        return item;
    }

    private static EntityItem dropAtPlayer(GameTestHelper helper, FakePlayer player, ItemStack stack) {
        return new EntityItem(helper.getWorld(), player.posX, player.posY, player.posZ, stack);
    }
}
