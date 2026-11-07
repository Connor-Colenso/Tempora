package com.colen.tempora.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkDataEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

// Scheduled block updates world gen leaves behind, a spring's water or lava still to flow (Hodgepodge defers those) or
// nether fire still to age. They run on later ticks, so they count as world gen through a mark on the update, which
// updates they schedule for their own block inherit, so the whole flow stays out of the log
//
// Only the update playing itself out counts, what it changes at its own position and its own block spreading into air.
// Anything else it changes gets logged since it might be someone's, e.g. generated fire burning a player's planks or a
// spring washing their torch away. Updates it schedules for other blocks, like a player's repeater next to the flow,
// aren't marked
//
// The mark expires a while after generation so it can't hide a later change forever, e.g. generated nether fire still
// ticking and spreading hours later. Only time with the chunk loaded counts, the mark is saved with the chunk's pending
// updates since pregenerated chunks unload before their updates run
public final class WorldGenTicks {

    // How long updates world gen left behind count as world gen, 5 minutes of loaded game time
    public static final long LIFETIME_TICKS = 6000;

    // Added to a saved pending update (in the chunk's TileTicks), the mark's remaining ticks
    public static final String NBT_KEY = "TemporaWorldGen";

    // World gen update running right now and its world, or null. Server thread only
    private static NextTickListEntry running;
    private static World runningWorld;

    private WorldGenTicks() {}

    // Mixed into NextTickListEntry, the world time its world gen mark expires at or 0 if none
    public interface Marked {

        long tempora$getWorldGenUntil();

        void tempora$setWorldGenUntil(long worldTime);
    }

    // Mark for an update scheduled now. Generation starts a new one, an update it left passes its own on to updates for
    // its own block
    public static long markForNewTick(World world, NextTickListEntry entry) {
        // Nothing to keep out of the log, and unmarked updates leave nothing of ours in the saved chunk
        if (!GenericUtils.shouldTemporaRun()) return 0;
        if (WorldGenPhaseTracker.isWorldGen()) return world.getTotalWorldTime() + LIFETIME_TICKS;
        return running != null && world == runningWorld && isOwnBlock(entry.func_151351_a()) ? until(running) : 0;
    }

    public static boolean isWorldGen(NextTickListEntry entry, World world) {
        return until(entry) > world.getTotalWorldTime();
    }

    // Runs a world gen update, isWorldGenChange decides which of its changes count as world gen
    public static void runAsWorldGen(NextTickListEntry entry, World world, Runnable update) {
        NextTickListEntry previous = running;
        World previousWorld = runningWorld;
        running = entry;
        runningWorld = world;
        try {
            update.run();
        } finally {
            running = previous;
            runningWorld = previousWorld;
        }
    }

    // Whether a block change is the running world gen update playing itself out, a change at its own position or its
    // own block spreading into air. after is the block being set, same as before for a meta change
    public static boolean isWorldGenChange(World world, int x, int y, int z, Block before, Block after) {
        if (running == null || world != runningWorld) return false;
        if (x == running.xCoord && y == running.yCoord && z == running.zCoord) return true;
        return isOwnBlock(after) && (before.getMaterial() == Material.air || isOwnBlock(before));
    }

    // The running update's block, or its fluid in the other form since still fluid the flow touches turns flowing
    private static boolean isOwnBlock(Block block) {
        Block own = running.func_151351_a();
        return block == own
            || block instanceof BlockLiquid && own instanceof BlockLiquid && block.getMaterial() == own.getMaterial();
    }

    private static long until(NextTickListEntry entry) {
        return ((Marked) entry).tempora$getWorldGenUntil();
    }

    // Saves the marks with the chunk's pending updates and reads them back. Through Forge's chunk data events not the
    // chunk loader itself, since mods replace how chunks get written (ChunkAPI)
    public static final class ChunkSaves {

        @SubscribeEvent
        public void onChunkSave(ChunkDataEvent.Save event) {
            // No marks get made while Tempora is off, and looking for them walks every pending update in the world
            if (!GenericUtils.shouldTemporaRun()) return;
            NBTTagList saved = savedUpdates(event);
            if (saved.tagCount() == 0) return;

            Map<NextTickListEntry, Long> remaining = new HashMap<>();
            for (NextTickListEntry entry : pendingUpdates(event)) {
                long ticks = until(entry) - event.world.getTotalWorldTime();
                if (ticks > 0) remaining.put(entry, ticks);
            }
            if (remaining.isEmpty()) return;

            for (int i = 0; i < saved.tagCount(); i++) {
                NBTTagCompound update = saved.getCompoundTagAt(i);
                Long ticks = remaining.get(asEntry(update));
                if (ticks != null) update.setLong(NBT_KEY, ticks);
            }
        }

        // After the chunk's pending updates were read back, so they're the world's again
        @SubscribeEvent
        public void onChunkLoad(ChunkDataEvent.Load event) {
            if (!GenericUtils.shouldTemporaRun()) return;
            NBTTagList saved = savedUpdates(event);

            Map<NextTickListEntry, Long> remaining = new HashMap<>();
            for (int i = 0; i < saved.tagCount(); i++) {
                NBTTagCompound update = saved.getCompoundTagAt(i);
                long ticks = update.getLong(NBT_KEY);
                if (ticks > 0) remaining.put(asEntry(update), ticks);
            }
            if (remaining.isEmpty()) return;

            for (NextTickListEntry entry : pendingUpdates(event)) {
                Long ticks = remaining.get(entry);
                if (ticks != null) {
                    ((Marked) entry).tempora$setWorldGenUntil(event.world.getTotalWorldTime() + ticks);
                }
            }
        }

        private static NBTTagList savedUpdates(ChunkDataEvent event) {
            return event.getData()
                .getCompoundTag("Level")
                .getTagList("TileTicks", 10);
        }

        private static List<NextTickListEntry> pendingUpdates(ChunkDataEvent event) {
            List<NextTickListEntry> pending = event.world.getPendingBlockUpdates(event.getChunk(), false);
            return pending == null ? Collections.emptyList() : pending;
        }

        // Pending updates are equal when position and block are, same as the world's own set compares them
        private static NextTickListEntry asEntry(NBTTagCompound update) {
            return new NextTickListEntry(
                update.getInteger("x"),
                update.getInteger("y"),
                update.getInteger("z"),
                Block.getBlockById(update.getInteger("i")));
        }
    }
}
