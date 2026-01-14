package com.colen.tempora.loggers.block_change.region_registry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.storage.ISaveHandler;

public final class BlockChangeRegionRegistry {

    private static final String DIR_NAME = "tempora";
    private static final String FILE_NAME = "tempora_blockchange_regions.dat";

    private boolean loaded = false;

    private static BlockChangeRegionRegistry instance;

    private final Map<Integer, List<TemporaWorldRegion>> byDim = new HashMap<>();
    private boolean dirty = false;

    // Public API.

    public static void add(TemporaWorldRegion r) {
        get().addRegion(r);
    }

    public static boolean containsBlock(int dim, int x, int y, int z) {
        return get().contains(dim, x, y, z);
    }

    public static List<TemporaWorldRegion> removeRegionsIntersectingPlayer(EntityPlayer player) {
        return get().removeIntersecting(player);
    }

    private List<TemporaWorldRegion> removeIntersecting(EntityPlayer player) {
        List<TemporaWorldRegion> list = byDim.get(player.dimension);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        AxisAlignedBB playerBB = player.boundingBox;
        List<TemporaWorldRegion> removed = new ArrayList<>();

        for (Iterator<TemporaWorldRegion> it = list.iterator(); it.hasNext();) {
            TemporaWorldRegion region = it.next();

            // Build region AABB (block-aligned; expand by 1 if your regions are inclusive)
            AxisAlignedBB regionBB = AxisAlignedBB.getBoundingBox(
                region.getMinX(),
                region.getMinY(),
                region.getMinZ(),
                region.getMaxX(),
                region.getMaxY(),
                region.getMaxZ());

            if (regionBB.intersectsWith(playerBB)) {
                it.remove();
                removed.add(region);
            }
        }

        if (!removed.isEmpty()) {
            dirty = true;
        }

        return removed;
    }

    private List<TemporaWorldRegion> removeContaining(EntityPlayer player) {
        List<TemporaWorldRegion> list = byDim.get(player.dimension);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<TemporaWorldRegion> removed = new ArrayList<>();

        for (Iterator<TemporaWorldRegion> it = list.iterator(); it.hasNext();) {
            TemporaWorldRegion region = it.next();

            if (region.containsBlock(player.dimension, player.posX, player.posY, player.posZ)) {

                it.remove();
                removed.add(region);
            }
        }

        if (!removed.isEmpty()) {
            dirty = true;
        }

        return removed;
    }

    public static List<TemporaWorldRegion> getAll() {
        return get().allRegions();
    }

    // Called on server shutdown.
    public static void saveIfDirty() {
        if (instance != null && instance.dirty) {
            instance.save();
        }
    }

    // Call on server startup.
    public static void loadNow() {
        BlockChangeRegionRegistry r = get();
        if (!r.loaded) {
            r.load();
            r.loaded = true;
        }
    }

    // Internal logic.

    private void addRegion(TemporaWorldRegion r) {
        byDim.computeIfAbsent(r.getDimID(), d -> new ArrayList<>())
            .add(r);
        dirty = true;
    }

    private boolean contains(int dim, int x, int y, int z) {
        List<TemporaWorldRegion> list = byDim.get(dim);
        if (list == null) return false;

        for (TemporaWorldRegion r : list) {
            if (r.containsBlock(dim, x, y, z)) return true;
        }
        return false;
    }

    private List<TemporaWorldRegion> allRegions() {
        List<TemporaWorldRegion> out = new ArrayList<>();
        List<Integer> dims = new ArrayList<>(byDim.keySet());
        Collections.sort(dims);

        for (Integer d : dims) {
            out.addAll(byDim.get(d));
        }
        return out;
    }

    // Persistence mechanism.

    private void load() {
        File file = getSaveFile();
        if (!file.exists()) return;

        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            readFromNBT(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RegionRegistry", e);
        }
    }

    private void save() {
        try {
            File file = getSaveFile();
            file.getParentFile()
                .mkdirs();

            NBTTagCompound root = new NBTTagCompound();
            writeToNBT(root);
            CompressedStreamTools.write(root, file);

            dirty = false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save RegionRegistry", e);
        }
    }

    private void readFromNBT(NBTTagCompound tag) {
        byDim.clear();

        NBTTagList list = tag.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            TemporaWorldRegion r = TemporaWorldRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.getDimID(), d -> new ArrayList<>())
                .add(r);
        }
    }

    private void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();

        for (List<TemporaWorldRegion> regions : byDim.values()) {
            for (TemporaWorldRegion r : regions) {
                list.appendTag(r.writeNBT());
            }
        }

        tag.setTag("regions", list);
    }

    // Singleton access

    private static BlockChangeRegionRegistry get() {
        if (instance == null) {
            instance = new BlockChangeRegionRegistry();
        }
        return instance;
    }

    private static File getSaveFile() {
        MinecraftServer server = MinecraftServer.getServer();
        ISaveHandler handler = server.getEntityWorld()
            .getSaveHandler();

        File root = handler.getWorldDirectory();
        return new File(new File(root, DIR_NAME), FILE_NAME);
    }

}
