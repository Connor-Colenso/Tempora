package com.colen.tempora.logging.loggers.block_change;

import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class RegionRegistry extends WorldSavedData {

    private static final String KEY = "TemporaRegions_v1";
    private static RegionRegistry instance;

    private final Map<Integer, List<IntRegion>> byDim = new HashMap<>();

    public RegionRegistry() { super(KEY); }
    public RegionRegistry(String name) { super(name); }

    // static API
    public static void add(IntRegion r) {
        get().addRegion(r);
    }

    public static boolean containsBlock(int dim, int x, int y, int z) {
        return get().contains(dim, x, y, z);
    }


    public static int removeRegionsContainingBlock(int dim, double x, double y, double z) {
        return get().removeContaining(dim, x, y, z);
    }

    public static List<IntRegion> getAll() { return get().allRegions(); }

    // instance logic
    private void addRegion(IntRegion r) {
        byDim.computeIfAbsent(r.dim, d -> new ArrayList<>()).add(r);
        markDirty();
    }

    private boolean contains(int dim, int x, int y, int z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null) return false;
        for (IntRegion r : list) if (r.contains(dim, x, y, z)) return true;
        return false;
    }

    private int removeContaining(int dim, double x, double y, double z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null) return 0;
        int removed = 0;
        for (Iterator<IntRegion> it = list.iterator(); it.hasNext(); ) {
            if (it.next().contains(dim, x, y, z)) { it.remove(); removed++; }
        }
        if (removed > 0) markDirty();
        return removed;
    }

    private List<IntRegion> allRegions() {
        List<IntRegion> out = new ArrayList<>();
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
            IntRegion r = IntRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.dim, d -> new ArrayList<>()).add(r);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (List<IntRegion> l : byDim.values())
            for (IntRegion r : l) list.appendTag(r.writeNBT());
        tag.setTag("regions", list);
    }

    // singleton access
    private static RegionRegistry get() {
        if (instance == null) {
            World overworld = MinecraftServer.getServer().worldServerForDimension(0);
            instance = (RegionRegistry) overworld.perWorldStorage.loadData(RegionRegistry.class, KEY);
            if (instance == null) {
                instance = new RegionRegistry();
                overworld.perWorldStorage.setData(KEY, instance);
            }
        }
        return instance;
    }
}
