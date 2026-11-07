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

// Server side store of every region, saved to the world folder
//
// Regions decide where loggers record. Each region can hold an ON or OFF rule per logger. For a position, the smallest
// region containing it with a rule for that logger decides. If same size regions disagree ON wins, a missing record is
// worse than an extra one. If no region has a rule the logger's logByDefault config applies
public final class TemporaRegionRegistry {

    private static final String DIR_NAME = "tempora";
    private static final String FILE_NAME = "regions.dat";

    // Swapped on server start/stop so one world's regions never leak into another (singleplayer)
    private static volatile TemporaRegionRegistry instance = new TemporaRegionRegistry();

    // Copy on write, loggers read these on every event (maybe off the server thread) and commands rarely change them
    private final Map<Integer, List<TemporaWorldRegion>> byDim = new ConcurrentHashMap<>();

    // Loggers with a rule in at least one region, every other logger skips the region lookup
    private volatile Set<String> loggersWithRules = Collections.emptySet();

    private boolean dirty = false;

    // Set when the region file couldn't be read on server start so ops get told when they join. Its current name, moved
    // aside if that worked otherwise its own
    private static volatile @Nullable String unreadableFileName;

    private TemporaRegionRegistry() {}

    // Server start, before any logger can query regions
    public static void load() {
        TemporaRegionRegistry registry = new TemporaRegionRegistry();
        unreadableFileName = null;

        File file = getSaveFile();
        if (file.exists()) {
            try {
                registry.readFromNBT(CompressedStreamTools.read(file));
            } catch (Exception e) {
                // An unreadable file can't stop the server starting. Kept next to the new one so the regions in it can
                // be recovered by hand
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

    // Server stop
    public static void unload() {
        TemporaRegionRegistry registry = instance;
        if (registry.dirty) {
            registry.save();
        }
        instance = new TemporaRegionRegistry();
        unreadableFileName = null;
    }

    // Region file this server couldn't read on start, null if it was fine (or there wasn't one)
    public static @Nullable String getUnreadableFileName() {
        return unreadableFileName;
    }

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

    // Several loggers at once, saves the file once instead of per logger
    public static void setRules(TemporaWorldRegion region, Iterable<String> loggerNames, LogRule rule) {
        for (String loggerName : loggerNames) region.putRule(loggerName, rule);
        instance.changed();
    }

    public static boolean clearRule(TemporaWorldRegion region, String loggerName) {
        if (!region.removeRule(loggerName)) return false;

        instance.changed();
        return true;
    }

    // Labels are unique, ignoring case
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

    // Fresh list, safe to sort or filter
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

    // Region whose rule decides if the logger records at this position, null if no region has a say and the logger's
    // default applies. See the class comment for how it's picked
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

    private void changed() {
        rebuildRuleIndex();
        dirty = true;
        // Regions change rarely and the file's small, so save straight away instead of risking edits on a crash. If it
        // fails the registry stays dirty and saving gets retried on server stop
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
            // Temp file first then moved over the old one, so a crash part way through leaves the old file alone
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
