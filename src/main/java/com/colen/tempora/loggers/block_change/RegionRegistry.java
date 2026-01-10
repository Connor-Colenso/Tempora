package com.colen.tempora.loggers.block_change;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.ISaveHandler;

public final class RegionRegistry {

    private static final String DIR_NAME = "tempora";
    private static final String FILE_NAME = "tempora_blockchange_regions.dat";

    private boolean loaded = false;

    private static RegionRegistry instance;

    private final Map<Integer, List<RenderRegionAlternatingCheckers>> byDim = new HashMap<>();
    private boolean dirty = false;

    // Public API.

    public static void add(RenderRegionAlternatingCheckers r) {
        get().addRegion(r);
    }

    public static boolean containsBlock(int dim, int x, int y, int z) {
        return get().contains(dim, x, y, z);
    }

    public static int removeRegionsContainingCoordinate(int dim, double x, double y, double z) {
        return get().removeContaining(dim, x, y, z);
    }

    public static List<RenderRegionAlternatingCheckers> getAll() {
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
        RegionRegistry r = get();
        if (!r.loaded) {
            r.load();
            r.loaded = true;
        }
    }

    // Internal logic.

    private void addRegion(RenderRegionAlternatingCheckers r) {
        byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
            .add(r);
        dirty = true;
    }

    private boolean contains(int dim, int x, int y, int z) {
        List<RenderRegionAlternatingCheckers> list = byDim.get(dim);
        if (list == null) return false;

        for (RenderRegionAlternatingCheckers r : list) {
            if (r.contains(dim, x, y, z)) return true;
        }
        return false;
    }

    private int removeContaining(int dim, double x, double y, double z) {
        List<RenderRegionAlternatingCheckers> list = byDim.get(dim);
        if (list == null) return 0;

        int removed = 0;
        for (Iterator<RenderRegionAlternatingCheckers> it = list.iterator(); it.hasNext();) {
            if (it.next()
                .contains(dim, x, y, z)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) dirty = true;
        return removed;
    }

    private List<RenderRegionAlternatingCheckers> allRegions() {
        List<RenderRegionAlternatingCheckers> out = new ArrayList<>();
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
            RenderRegionAlternatingCheckers r = RenderRegionAlternatingCheckers.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
                .add(r);
        }
    }

    private void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();

        for (List<RenderRegionAlternatingCheckers> regions : byDim.values()) {
            for (RenderRegionAlternatingCheckers r : regions) {
                list.appendTag(r.writeNBT());
            }
        }

        tag.setTag("regions", list);
    }

    // Singleton access

    private static RegionRegistry get() {
        if (instance == null) {
            instance = new RegionRegistry();
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
