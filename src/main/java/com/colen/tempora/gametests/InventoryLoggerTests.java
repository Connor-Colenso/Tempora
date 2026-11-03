package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.assertCount;
import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.tileentity.TileEntityChest;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.inventory.InventoryEventInfo;
import com.colen.tempora.loggers.inventory.InventoryLogger;
import com.colen.tempora.loggers.inventory.InventoryTransactionTracker;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * The inventory logger, which compares what a player and the containers they have open held before and after each GUI
 * click. The tests click a chest's container on the server, between the same tracker hooks MixinNetworkManager wraps a
 * real click packet in.
 */
@GameTestHolder(Tempora.MODID)
public class InventoryLoggerTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 200;

    // Container slot of the player's first hotbar slot, after a chest's slots and the player's 27 main slots.
    private static final int SINGLE_CHEST_HOTBAR_0 = 27 + 27;
    private static final int DOUBLE_CHEST_HOTBAR_0 = 54 + 27;

    private static final int LEFT_CLICK = 0;
    private static final int PICK_UP = 0;
    private static final int SHIFT_CLICK = 1;

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void itemsPutInChestAreLogged(GameTestHelper helper) {
        InventoryLogger logger = TemporaEvents.inventoryLogger;
        EntityPlayerMP player = player(helper, "inv_put");
        TestPos chestPos = helper.absolute(at(2, 1, 2));
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "itemsPutInChestAreLogged");

        helper.startSequence()
            .thenExecute("move 10 cobblestone from the hotbar into the chest", () -> {
                TileEntityChest chest = placeChest(helper, at(2, 1, 2));
                player.inventory.mainInventory[0] = new ItemStack(Blocks.cobblestone, 10);
                player.openContainer = new ContainerChest(player.inventory, chest);

                click(player, SINGLE_CHEST_HOTBAR_0, LEFT_CLICK, PICK_UP);
                click(player, 0, LEFT_CLICK, PICK_UP);

                helper.assertNotNull(chest.getStackInSlot(0), "nothing went into the chest");
            })
            .thenWaitUntil(
                "the move is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, chestPos, 0)), 1))
            .thenExecute("event reads back intact", () -> {
                InventoryEventInfo event = (InventoryEventInfo) probe.latest()
                    .get(0);
                assertChange(helper, event, player, Item.getItemFromBlock(Blocks.cobblestone), 10, true);
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void itemsTakenFromChestAreLogged(GameTestHelper helper) {
        InventoryLogger logger = TemporaEvents.inventoryLogger;
        EntityPlayerMP player = player(helper, "inv_take");
        TestPos chestPos = helper.absolute(at(2, 1, 2));
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "itemsTakenFromChestAreLogged");

        helper.startSequence()
            .thenExecute("shift click 5 dirt out of the chest", () -> {
                TileEntityChest chest = placeChest(helper, at(2, 1, 2));
                chest.setInventorySlotContents(0, new ItemStack(Blocks.dirt, 5));
                player.openContainer = new ContainerChest(player.inventory, chest);

                click(player, 0, LEFT_CLICK, SHIFT_CLICK);

                helper.assertNull(chest.getStackInSlot(0), "the dirt is still in the chest");
            })
            .thenWaitUntil(
                "the move is logged",
                WRITE_TICKS,
                () -> assertCount(helper, probe.poll(() -> eventsNear(helper, logger, chestPos, 0)), 1))
            .thenExecute("event reads back intact", () -> {
                InventoryEventInfo event = (InventoryEventInfo) probe.latest()
                    .get(0);
                assertChange(helper, event, player, Item.getItemFromBlock(Blocks.dirt), 5, false);
                assertWellFormed(helper, logger, probe.latest());
            })
            .thenSucceed();
    }

    // A double chest is two chests. Items put in it are logged at the half that holds them, not the one clicked.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void doubleChestLogsAtTheHalfUsed(GameTestHelper helper) {
        InventoryLogger logger = TemporaEvents.inventoryLogger;
        EntityPlayerMP player = player(helper, "inv_double");
        TileEntityChest[] halves = new TileEntityChest[2];
        TileEntityChest[] holder = new TileEntityChest[1];
        Probe<List<PositionalEventInfo>> holding = new Probe<>(helper, "doubleChestLogsAtTheHalfUsed.holding");
        Probe<List<PositionalEventInfo>> other = new Probe<>(helper, "doubleChestLogsAtTheHalfUsed.other");

        helper.startSequence()
            .thenExecute("put 3 gold ingots in the second half of a double chest", () -> {
                halves[0] = placeChest(helper, at(1, 1, 2));
                halves[1] = placeChest(helper, at(2, 1, 2));
                TestPos clicked = helper.absolute(at(1, 1, 2));
                // The double chest's inventory, as opening it in game would give.
                IInventory doubleChest = Blocks.chest
                    .func_149951_m(helper.getWorld(), clicked.x(), clicked.y(), clicked.z());
                helper.assertEquals(54L, doubleChest.getSizeInventory(), "double chest size");

                player.inventory.mainInventory[0] = new ItemStack(Items.gold_ingot, 3);
                player.openContainer = new ContainerChest(player.inventory, doubleChest);
                click(player, DOUBLE_CHEST_HOTBAR_0, LEFT_CLICK, PICK_UP);
                click(player, 27, LEFT_CLICK, PICK_UP);

                holder[0] = halves[0].getStackInSlot(0) != null ? halves[0] : halves[1];
                helper.assertNotNull(holder[0].getStackInSlot(0), "the ingots are in neither half");
            })
            .thenWaitUntil(
                "the move is logged at the half holding the ingots",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    holding.poll(
                        () -> eventsNear(
                            logger,
                            helper.getWorld().provider.dimensionId,
                            holder[0].xCoord,
                            holder[0].yCoord,
                            holder[0].zCoord,
                            0)),
                    1))
            .thenWaitUntil("nothing is logged at the other half", WRITE_TICKS, () -> {
                TileEntityChest otherHalf = holder[0] == halves[0] ? halves[1] : halves[0];
                assertCount(
                    helper,
                    other.poll(
                        () -> eventsNear(
                            logger,
                            helper.getWorld().provider.dimensionId,
                            otherHalf.xCoord,
                            otherHalf.yCoord,
                            otherHalf.zCoord,
                            0)),
                    0);
            })
            .thenExecute("event reads back intact", () -> {
                InventoryEventInfo event = (InventoryEventInfo) holding.latest()
                    .get(0);
                assertChange(helper, event, player, Items.gold_ingot, 3, true);
            })
            .thenSucceed();
    }

    // ---------- Helpers ----------

    private static TileEntityChest placeChest(GameTestHelper helper, TestPos local) {
        helper.setBlock(local, Blocks.chest);
        return helper.assertTileEntityPresent(TileEntityChest.class, local);
    }

    /**
     * Clicks a slot of the player's open container, as handling a click packet does, inside the inventory tracker's
     * before and after hooks.
     */
    private static void click(EntityPlayerMP player, int slot, int button, int mode) {
        // Only its type matters to the tracker. Its other constructor is client side only.
        C0EPacketClickWindow packet = new C0EPacketClickWindow();
        InventoryTransactionTracker.PacketToken token = InventoryTransactionTracker.beforePacket(player, packet);
        try {
            player.openContainer.slotClick(slot, button, mode, player);
        } finally {
            InventoryTransactionTracker.afterPacket(token);
        }
    }

    private static void assertChange(GameTestHelper helper, InventoryEventInfo event, EntityPlayerMP player, Item item,
        int amount, boolean intoContainer) {
        helper.assertEquals((long) Item.getIdFromItem(item), event.itemID, "itemID");
        helper.assertEquals((long) amount, event.amount, "amount");
        helper.assertEquals(
            (intoContainer ? InventoryLogger.Direction.IN_TO_CONTAINER : InventoryLogger.Direction.OUT_OF_CONTAINER)
                .getDbId(),
            event.direction,
            "direction");
        helper.assertEquals(
            player.getUniqueID()
                .toString(),
            event.playerUUID,
            "playerUUID");
    }
}
