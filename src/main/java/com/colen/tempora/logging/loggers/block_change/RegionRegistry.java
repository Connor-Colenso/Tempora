package com.colen.tempora.logging.loggers.block_change;

import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Global (per‑save) registry of rectangular regions keyed by dimension.
 *
 * Use only the static helpers — you never need to hold onto the instance.
 */
public class RegionRegistry extends WorldSavedData {

    private static final String KEY = "TemporaRegions_v1";

    /* singleton kept inside the class */
    private static RegionRegistry INSTANCE;

    /* Map<dimension id, List<regions in that dim>>  (instance, not static!) */
    private final Map<Integer, List<IntRegion>> byDim = new HashMap<>();

    /* ────────────────────────────────  Constructors  ─────────────────────────────── */

    public RegionRegistry() { super(KEY); }
    /** Only called by MapStorage when loading */
    public RegionRegistry(String name) { super(name); }

    /* ────────────────────────────────  Static API  ──────────────────────────────── */

    /** Add a region and mark the data dirty */
    public static void add(IntRegion r) { ensure().add0(r); }

    /** Remove a region (if present) and mark dirty */
    public static void remove(IntRegion r) { ensure().remove0(r); }

    /** True if any stored region contains the given block position */
    public static boolean containsBlock(int dim,int x,int y,int z) {
        return ensure().containsBlock0(dim,x,y,z);
    }

    /** Remove every region that contains the block, return # removed */
    public static int removeRegionsContainingBlock(int dim,int x,int y,int z) {
        return ensure().removeRegionsContainingBlock0(dim,x,y,z);
    }

    /** Snapshot list of every region, sorted by dimension ID */
    public static List<IntRegion> getAll() { return ensure().getAll0(); }

    private void add0(IntRegion r) {
        byDim.computeIfAbsent(r.dim,d->new ArrayList<>()).add(r);
        markDirty();
    }

    private void remove0(IntRegion r) {
        Optional.ofNullable(byDim.get(r.dim))
            .ifPresent(list -> { if (list.remove(r)) markDirty(); });
    }

    private boolean containsBlock0(int dim,int x,int y,int z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null) return false;
        for (IntRegion r:list) if (r.contains(dim,x,y,z)) return true;
        return false;
    }

    private int removeRegionsContainingBlock0(int dim,int x,int y,int z) {
        List<IntRegion> list = byDim.get(dim);
        if (list == null || list.isEmpty()) return 0;

        int removed = 0;
        for (Iterator<IntRegion> it=list.iterator(); it.hasNext();) {
            IntRegion r = it.next();
            if (r.contains(dim,x,y,z)) { it.remove(); removed++; }
        }
        if (removed>0) markDirty();
        return removed;
    }

    private List<IntRegion> getAll0() {
        List<IntRegion> all = new ArrayList<>();
        List<Integer> dims = new ArrayList<>(byDim.keySet());
        Collections.sort(dims);
        for (Integer dim:dims) all.addAll(byDim.get(dim));
        return all;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        byDim.clear();
        NBTTagList list = tag.getTagList("regions",10);         // 10 = compound
        for (int i=0;i<list.tagCount();i++) {
            IntRegion r = IntRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(r.dim,d->new ArrayList<>()).add(r);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (List<IntRegion> l:byDim.values())
            for (IntRegion r:l) list.appendTag(r.writeNBT());
        tag.setTag("regions", list);
    }

    /** Lazily load (or create) the singleton from the overworld’s MapStorage. */
    private static RegionRegistry ensure() {
        if (INSTANCE == null) {
            World overworld = net.minecraft.server.MinecraftServer.getServer()
                .worldServerForDimension(0);
            INSTANCE = (RegionRegistry) overworld.perWorldStorage
                .loadData(RegionRegistry.class, KEY);
            if (INSTANCE == null) {
                INSTANCE = new RegionRegistry();
                overworld.perWorldStorage.setData(KEY, INSTANCE);
            }
        }
        return INSTANCE;
    }
}
