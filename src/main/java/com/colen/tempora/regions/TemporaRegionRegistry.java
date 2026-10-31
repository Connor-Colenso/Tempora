package com.colen.tempora.regions;

import static com.colen.tempora.Tempora.LOG;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.ISaveHandler;

import org.jetbrains.annotations.Nullable;

/**
 * Server side store of every region, saved to the world folder.
 * <p>
 * Regions decide where loggers record events. Each region can hold an ON or OFF rule per logger. For a given
 * position, the smallest region containing it that has a rule for the logger decides. If equally sized regions
 * disagree, ON wins, since a missing record is worse than an extra one. If no region has a rule, the logger's
 * logByDefault config option applies.
 */
public final class TemporaRegionRegistry {

    private static final String DIR_NAME = "tempora";
    private static final String FILE_NAME = "tempora_region_registry.dat";

    // Swapped out on server start/stop, so one world's regions never leak into another (e.g. in singleplayer).
    private static volatile TemporaRegionRegistry instance = new TemporaRegionRegistry();

    // Copy-on-write: loggers read these on every event, possibly off the server thread, while commands rarely
    // change them.
    private final Map<Integer, List<TemporaWorldRegion>> byDim = new ConcurrentHashMap<>();

    // Loggers that at least one region has a rule for. Every other logger skips the region lookup entirely.
    private volatile Set<String> loggersWithRules = Collections.emptySet();

    private boolean dirty = false;

    // Set when the region file could not be read on server start, so ops can be told as they join. The name it has
    // now: moved aside if that worked, else its own.
    private static volatile @Nullable String unreadableFileName;

    private TemporaRegionRegistry() {}

    /* ---------- Lifecycle ---------- */

    // Call on server start, before any logger can query regions.
    public static void load() {
        TemporaRegionRegistry registry = new TemporaRegionRegistry();
        unreadableFileName = null;

        File file = getSaveFile();
        if (file.exists()) {
            try {
                registry.readFromNBT(CompressedStreamTools.read(file));
            } catch (Exception e) {
                // A file that can't be read must not stop the server starting. It is kept beside the new one, so the
                // regions in it can still be recovered by hand.
                File kept = new File(
                    file.getParentFile(),
                    file.getName() + ".unreadable-" + System.currentTimeMillis());
                boolean moved = file.renameTo(kept);
                LOG.error(
                    "Could not read Tempora's regions from {}, so the server starts with none. {}",
                    file,
                    moved ? "The file was moved to " + kept + "." : "The file could not be moved aside.",
                    e);
                unreadableFileName = (moved ? kept : file).getName();
                registry = new TemporaRegionRegistry();
            }
        }

        registry.rebuildRuleIndex();
        instance = registry;
    }

    // Call on server stop.
    public static void unload() {
        TemporaRegionRegistry registry = instance;
        if (registry.dirty) {
            registry.save();
        }
        instance = new TemporaRegionRegistry();
        unreadableFileName = null;
    }

    // The region file this server could not read when it started, or null if it read fine (or there was none).
    public static @Nullable String getUnreadableFileName() {
        return unreadableFileName;
    }

    /* ---------- Editing ---------- */

    public static void add(TemporaWorldRegion region) {
        TemporaRegionRegistry registry = instance;
        registry.byDim.computeIfAbsent(region.getDimID(), d -> new CopyOnWriteArrayList<>())
            .add(region);
        registry.changed();
    }

    public static boolean remove(TemporaWorldRegion region) {
        TemporaRegionRegistry registry = instance;
        List<TemporaWorldRegion> regions = registry.byDim.get(region.getDimID());
        if (regions == null || !regions.remove(region)) return false;

        registry.changed();
        return true;
    }

    public static void setRule(TemporaWorldRegion region, String loggerName, LogRule rule) {
        region.putRule(loggerName, rule);
        instance.changed();
    }

    // Several loggers at once, saving the file once rather than for each.
    public static void setRules(TemporaWorldRegion region, Iterable<String> loggerNames, LogRule rule) {
        for (String loggerName : loggerNames) region.putRule(loggerName, rule);
        instance.changed();
    }

    public static boolean clearRule(TemporaWorldRegion region, String loggerName) {
        if (!region.removeRule(loggerName)) return false;

        instance.changed();
        return true;
    }

    /* ---------- Lookup ---------- */

    // Labels are unique, ignoring case.
    public static @Nullable TemporaWorldRegion getByLabel(String label) {
        for (List<TemporaWorldRegion> regions : instance.byDim.values()) {
            for (TemporaWorldRegion region : regions) {
                if (region.getLabel()
                    .equalsIgnoreCase(label)) {
                    return region;
                }
            }
        }
        return null;
    }

    public static List<String> getAllLabels() {
        List<String> labels = new ArrayList<>();
        for (TemporaWorldRegion region : getAll()) {
            labels.add(region.getLabel());
        }
        return labels;
    }

    // A fresh list, safe to sort or filter.
    public static List<TemporaWorldRegion> getAll() {
        List<TemporaWorldRegion> all = new ArrayList<>();
        for (List<TemporaWorldRegion> regions : instance.byDim.values()) {
            all.addAll(regions);
        }
        return all;
    }

    public static List<TemporaWorldRegion> regionsContaining(int dimID, double x, double y, double z) {
        List<TemporaWorldRegion> regions = instance.byDim.get(dimID);
        if (regions == null) return Collections.emptyList();

        List<TemporaWorldRegion> containing = new ArrayList<>();
        for (TemporaWorldRegion region : regions) {
            if (region.contains(dimID, x, y, z)) {
                containing.add(region);
            }
        }
        return containing;
    }

    /**
     * Finds the region whose rule decides whether the logger records an event at this position, or null if no
     * region has a say and the logger's own default applies. See the class comment for how the region is chosen.
     */
    public static @Nullable TemporaWorldRegion findDecidingRegion(String loggerName, int dimID, double x, double y,
        double z) {
        TemporaRegionRegistry registry = instance;
        if (!registry.loggersWithRules.contains(loggerName)) return null;

        List<TemporaWorldRegion> regions = registry.byDim.get(dimID);
        if (regions == null) return null;

        TemporaWorldRegion deciding = null;
        double decidingVolume = Double.MAX_VALUE;

        for (TemporaWorldRegion region : regions) {
            LogRule rule = region.getRule(loggerName);
            if (rule == null || !region.contains(dimID, x, y, z)) continue;

            double volume = region.getVolume();
            boolean smaller = volume < decidingVolume;
            boolean tieThatOnWins = volume == decidingVolume && rule == LogRule.ON;

            if (smaller || tieThatOnWins) {
                deciding = region;
                decidingVolume = volume;
            }
        }

        return deciding;
    }

    /* ---------- Internals ---------- */

    private void changed() {
        rebuildRuleIndex();
        dirty = true;
        // Regions change rarely and the file is small, so save straight away rather than risk losing edits to a
        // crash. If this fails, the registry stays dirty and saving is retried on server stop.
        save();
    }

    private void rebuildRuleIndex() {
        Set<String> names = new HashSet<>();
        for (List<TemporaWorldRegion> regions : byDim.values()) {
            for (TemporaWorldRegion region : regions) {
                names.addAll(
                    region.getRules()
                        .keySet());
            }
        }
        loggersWithRules = names;
    }

    private void save() {
        try {
            File file = getSaveFile();
            file.getParentFile()
                .mkdirs();

            NBTTagCompound root = new NBTTagCompound();
            writeToNBT(root);
            // To a temporary file first, then moved over the old one, so a crash part way through leaves the old file.
            CompressedStreamTools.safeWrite(root, file);

            dirty = false;
        } catch (Exception e) {
            LOG.error("Failed to save Tempora regions, will retry on server stop.", e);
        }
    }

    private void readFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            TemporaWorldRegion region = TemporaWorldRegion.readNBT(list.getCompoundTagAt(i));
            byDim.computeIfAbsent(region.getDimID(), d -> new CopyOnWriteArrayList<>())
                .add(region);
        }
    }

    private void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();

        for (List<TemporaWorldRegion> regions : byDim.values()) {
            for (TemporaWorldRegion region : regions) {
                list.appendTag(region.writeNBT());
            }
        }

        tag.setTag("regions", list);
    }

    private static File getSaveFile() {
        MinecraftServer server = MinecraftServer.getServer();
        ISaveHandler handler = server.getEntityWorld()
            .getSaveHandler();

        File root = handler.getWorldDirectory();
        return new File(new File(root, DIR_NAME), FILE_NAME);
    }

}
