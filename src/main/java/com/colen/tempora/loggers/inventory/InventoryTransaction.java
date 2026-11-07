package com.colen.tempora.loggers.inventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.colen.tempora.TemporaEvents;

// Everything one packet did to a player's items while they had a container open
//
// Almost everything moving through a GUI passes through the player's inventory or cursor, so we compare the player's
// inventory before and after, plus every container slot backed by a TE or entity. Changes to those slots get logged
// where they happened. Whatever the player gained or lost that those slots (and their own dropped items) don't explain
// must've come from or gone into the container itself, e.g. an AE2 network or a ModularUI machine whose slots hide
// what's behind them, and gets logged against the container's host
//
// Compared by item, meta and NBT, so a swap shows up as a removal and an addition instead of a count change
public final class InventoryTransaction {

    private final EntityPlayerMP player;
    private final Container container;
    // Creative middle click copies an item instead of taking it
    private final boolean creativeCopy;
    // Every non player slot is backed by a TE or entity, so nothing can move unseen
    private final boolean allStorageVisible;

    private final List<Slot> storageSlots = new ArrayList<>();
    private final List<WorldStorage> slotStorages = new ArrayList<>();
    private final List<StackState> playerBefore;
    private final List<StackState> storageBefore;

    // Items that appeared in the world as item entities while this packet was handled
    private final ItemCounts spawnedInWorld = new ItemCounts();

    // Changes other mods reported through the API during this packet. Already logged, but they still explain part of
    // the player's change same as a visible storage slot
    private final ItemCounts reportedChanges = new ItemCounts();

    InventoryTransaction(EntityPlayerMP player, Container container, boolean creativeCopy) {
        this.player = player;
        this.container = container;
        this.creativeCopy = creativeCopy;

        boolean hasOtherSlots = false;
        for (Slot slot : container.inventorySlots) {
            // Player's own items get compared as a whole below
            if (slot.inventory == player.inventory) continue;

            WorldStorage storage = WorldStorage.of(slot);
            if (storage == null) {
                hasOtherSlots = true;
            } else {
                storageSlots.add(slot);
                slotStorages.add(storage);
            }
        }
        this.allStorageVisible = !storageSlots.isEmpty() && !hasOtherSlots
            && !ContainerHosts.hasHiddenStorage(container);

        this.playerBefore = snapshotPlayer(true);
        this.storageBefore = snapshotStorage(true);
    }

    void recordSpawnedItem(ItemStack stack) {
        spawnedInWorld.add(ItemKey.snapshotOf(stack), stack.stackSize);
    }

    boolean isFor(EntityPlayer player) {
        return this.player == player;
    }

    void recordReportedChange(ItemKey item, long change) {
        reportedChanges.add(item, change);
    }

    void finish() {
        List<StackState> playerAfter = snapshotPlayer(false);
        List<StackState> storageAfter = snapshotStorage(false);

        // A stack instance still around afterwards whose NBT changed in place (wireless terminal using power, backpack
        // contents) is the same item, not a swap
        Map<ItemStack, ItemKey> keyAfter = new IdentityHashMap<>();
        for (StackState state : playerAfter) if (state != null) keyAfter.put(state.stack, state.key);
        for (StackState state : storageAfter) if (state != null) keyAfter.put(state.stack, state.key);

        Map<ItemKey, Long> playerChange = ItemCounts
            .difference(count(playerBefore, keyAfter), count(playerAfter, keyAfter));

        // Per storage, then summed
        Map<WorldStorage, ItemCounts> storageCountsBefore = new LinkedHashMap<>();
        Map<WorldStorage, ItemCounts> storageCountsAfter = new LinkedHashMap<>();
        for (int i = 0; i < storageSlots.size(); i++) {
            WorldStorage storage = slotStorages.get(i);
            add(storageCountsBefore.computeIfAbsent(storage, s -> new ItemCounts()), storageBefore.get(i), keyAfter);
            add(storageCountsAfter.computeIfAbsent(storage, s -> new ItemCounts()), storageAfter.get(i), keyAfter);
        }

        InventoryLogger logger = TemporaEvents.inventoryLogger;
        ItemCounts storageChangeTotal = new ItemCounts();

        for (Map.Entry<WorldStorage, ItemCounts> entry : storageCountsBefore.entrySet()) {
            WorldStorage storage = entry.getKey();
            Map<ItemKey, Long> change = ItemCounts.difference(entry.getValue(), storageCountsAfter.get(storage));

            for (Map.Entry<ItemKey, Long> itemChange : change.entrySet()) {
                storageChangeTotal.add(itemChange.getKey(), itemChange.getValue());
                logger.logChange(
                    player,
                    storage.name(),
                    storage.dimension(),
                    storage.x(),
                    storage.y(),
                    storage.z(),
                    itemChange.getKey(),
                    itemChange.getValue());
            }
        }

        for (Map.Entry<ItemKey, Long> reported : reportedChanges.asMap()
            .entrySet()) {
            storageChangeTotal.add(reported.getKey(), reported.getValue());
        }

        if (creativeCopy || allStorageVisible) return;

        Set<ItemKey> items = new LinkedHashSet<>(playerChange.keySet());
        items.addAll(
            spawnedInWorld.asMap()
                .keySet());

        ContainerHost host = null;
        for (ItemKey item : items) {
            long change = hostChange(
                playerChange.getOrDefault(item, 0L),
                storageChangeTotal.get(item),
                spawnedInWorld.get(item));
            if (change == 0) continue;

            if (host == null) {
                host = ContainerHosts.resolve(player, container, slotStorages.isEmpty() ? null : slotStorages.get(0));
            }

            logger.logChange(
                player,
                host.name(),
                host.dimension(player),
                host.x(player),
                host.y(player),
                host.z(player),
                item,
                change);
        }
    }

    // How much of an item went into (positive) or out of (negative) the container itself, from what the player gained,
    // what the visible storage slots gained and what got dropped into the world
    static long hostChange(long playerChange, long storageChange, long spawnedInWorld) {
        // Items the player threw out of their inventory or cursor never reached the container
        long droppedByPlayer = playerChange < 0 ? Math.min(spawnedInWorld, -playerChange) : 0;
        long playerLeft = playerChange + droppedByPlayer;
        long droppedFromContainer = spawnedInWorld - droppedByPlayer;

        // Movement between the player and visible storage slots is already logged against those slots
        long storageLeft = storageChange;
        if (playerLeft > 0 && storageLeft < 0) {
            long matched = Math.min(playerLeft, -storageLeft);
            playerLeft -= matched;
            storageLeft += matched;
        } else if (playerLeft < 0 && storageLeft > 0) {
            long matched = Math.min(-playerLeft, storageLeft);
            playerLeft += matched;
            storageLeft -= matched;
        }

        // Same for items thrown out of those slots
        if (droppedFromContainer > 0 && storageLeft < 0) {
            droppedFromContainer -= Math.min(droppedFromContainer, -storageLeft);
        }

        return -playerLeft - droppedFromContainer;
    }

    private List<StackState> snapshotPlayer(boolean copyNBT) {
        InventoryPlayer inventory = player.inventory;
        List<StackState> states = new ArrayList<>(inventory.mainInventory.length + inventory.armorInventory.length + 1);
        for (ItemStack stack : inventory.mainInventory) states.add(StackState.of(stack, copyNBT));
        for (ItemStack stack : inventory.armorInventory) states.add(StackState.of(stack, copyNBT));
        states.add(StackState.of(inventory.getItemStack(), copyNBT));
        return states;
    }

    private List<StackState> snapshotStorage(boolean copyNBT) {
        List<StackState> states = new ArrayList<>(storageSlots.size());
        for (Slot slot : storageSlots) states.add(StackState.of(slot.getStack(), copyNBT));
        return states;
    }

    private static ItemCounts count(List<StackState> states, Map<ItemStack, ItemKey> keyAfter) {
        ItemCounts counts = new ItemCounts();
        for (StackState state : states) add(counts, state, keyAfter);
        return counts;
    }

    private static void add(ItemCounts counts, @Nullable StackState state, Map<ItemStack, ItemKey> keyAfter) {
        if (state == null) return;
        ItemKey key = keyAfter.get(state.stack);
        counts.add(key != null ? key : state.key, state.size);
    }

    // A slot's stack at one moment, the stack object, what it was then and how many
    private static final class StackState {

        final ItemStack stack;
        final ItemKey key;
        final int size;

        private StackState(ItemStack stack, ItemKey key, int size) {
            this.stack = stack;
            this.key = key;
            this.size = size;
        }

        static @Nullable StackState of(@Nullable ItemStack stack, boolean copyNBT) {
            ItemKey key = copyNBT ? ItemKey.snapshotOf(stack) : ItemKey.of(stack);
            if (key == null) return null;
            return new StackState(stack, key, stack.stackSize);
        }
    }
}
