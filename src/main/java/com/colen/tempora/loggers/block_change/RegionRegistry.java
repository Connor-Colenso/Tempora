package com.colen.tempora.loggers.block_change;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class RegionRegistry extends WorldSavedData {

    // Avoid changing at all costs.
    private static final String KEY = "TemporaRegions_v1";
    private static RegionRegistry instance;

    private final Map<Integer, List<BlockChangeRecordingRegion>> byDim = new HashMap<>();

    public RegionRegistry() {
        super(KEY);
    }

    public RegionRegistry(String name) {
        super(name);
    }

    // static API
    public static void add(BlockChangeRecordingRegion r) {
        get().addRegion(r);
    }

    public static boolean containsBlock(int dim, int x, int y, int z) {
        return get().contains(dim, x, y, z);
    }

    public static int removeRegionsContainingCoordinate(int dim, double x, double y, double z) {
        return get().removeContaining(dim, x, y, z);
    }

    public static List<BlockChangeRecordingRegion> getAll() {
        return get().allRegions();
    }

    // instance logic
    private void addRegion(BlockChangeRecordingRegion r) {
        byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
            .add(r);
        markDirty();
    }

    private boolean contains(int dim, int x, int y, int z) {
        List<BlockChangeRecordingRegion> list = byDim.get(dim);
        if (list == null) return false;
        for (BlockChangeRecordingRegion r : list) if (r.contains(dim, x, y, z)) return true;
        return false;
    }

    private int removeContaining(int dim, double x, double y, double z) {
        List<BlockChangeRecordingRegion> list = byDim.get(dim);
        if (list == null) return 0;
        int removed = 0;
        for (Iterator<BlockChangeRecordingRegion> it = list.iterator(); it.hasNext();) {
            if (it.next()
                .contains(dim, x, y, z)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) markDirty();
        return removed;
    }

    private List<BlockChangeRecordingRegion> allRegions() {
        List<BlockChangeRecordingRegion> out = new ArrayList<>();
        List<Integer> dims = new ArrayList<>(byDim.keySet());
        Collections.sort(dims);
        for (Integer d : dims) out.addAll(byDim.get(d));
        return out;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        byDim.clear();
        NBTTagList list = tag.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            BlockChangeRecordingRegion r = BlockChangeRecordingRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
                .add(r);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (List<BlockChangeRecordingRegion> l : byDim.values())
            for (BlockChangeRecordingRegion r : l) list.appendTag(r.writeNBT());
        tag.setTag("regions", list);
    }

    // Singleton access
    private static RegionRegistry get() {
        if (instance == null) {
            // Hard code to dim 0, so we can store all info in one dim, even know each has dim int data. This makes data
            // retrieval much easier programmatically.
            World overworld = MinecraftServer.getServer()
                .worldServerForDimension(0);
            instance = (RegionRegistry) overworld.perWorldStorage.loadData(RegionRegistry.class, KEY);
            if (instance == null) {
                instance = new RegionRegistry();
                overworld.perWorldStorage.setData(KEY, instance);
            }
        }
        return instance;
    }
}
