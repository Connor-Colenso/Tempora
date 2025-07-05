package com.colen.tempora.logging.loggers.block_change;

import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class RegionRegistry extends WorldSavedData {

    private static final String KEY = "MyModRegions";

    /* Map<dimension id, List<regions in that dim>> */
    private final Map<Integer, List<IntRegion>> byDim = new HashMap<>();

    public RegionRegistry() {
        super(KEY);
    }

    public RegionRegistry(String name) {
        super(name);
    }

    public void add(IntRegion r) {
        byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
            .add(r);
        markDirty();
    }

    public void remove(IntRegion r) {
        Optional.ofNullable(byDim.get(r.dim))
            .ifPresent(list -> { if (list.remove(r)) markDirty(); });
    }

    public boolean containsBlock(int dim, int x, int y, int z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null) return false;
        for (IntRegion r : list) if (r.contains(dim, x, y, z)) return true;
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        byDim.clear();
        NBTTagList list = tag.getTagList("regions", 10); // 10 = compound
        for (int i = 0; i < list.tagCount(); i++) {
            IntRegion r = IntRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.dim, d -> new ArrayList<>())
                .add(r);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (List<IntRegion> l : byDim.values()) for (IntRegion r : l) list.appendTag(r.writeNBT());
        tag.setTag("regions", list);
    }

    public static RegionRegistry get(World world) {
        RegionRegistry data = (RegionRegistry) world.perWorldStorage.loadData(RegionRegistry.class, KEY);
        if (data == null) {
            data = new RegionRegistry();
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    public int removeRegionsContainingBlock(int dim, int x, int y, int z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null || list.isEmpty()) return 0;

        int removed = 0;
        for (Iterator<IntRegion> it = list.iterator(); it.hasNext();) {
            IntRegion r = it.next();
            if (r.contains(dim, x, y, z)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) markDirty();
        return removed;
    }
}
