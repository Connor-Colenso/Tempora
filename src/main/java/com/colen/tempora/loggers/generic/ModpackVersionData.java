package com.colen.tempora.loggers.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * World-global modpack/environment version registry.
 * Stores a stable mapping of environment-hash -> int ID.
 * Access CURRENT_VERSION after the world loads.
 */
public final class ModpackVersionData extends WorldSavedData {

    public static final String DATA_NAME = "tempora_modpack_versions";

    public static int CURRENT_VERSION = -1;

    private int nextId = 1;
    private final Map<Integer, String> versions = new HashMap<>();

    /* ------------------------------------------------------------ */

    @SuppressWarnings("unused")
    public ModpackVersionData() {
        super(DATA_NAME);
    }

    public ModpackVersionData(String name) {
        super(name);
    }

    /* ------------------------------------------------------------ */

    public static void init() {
        World overworld = MinecraftServer.getServer()
            .worldServerForDimension(0);

        MapStorage storage = overworld.mapStorage;

        ModpackVersionData data = (ModpackVersionData) storage.loadData(ModpackVersionData.class, DATA_NAME);

        if (data == null) {
            data = new ModpackVersionData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }

        CURRENT_VERSION = data.getOrCreateId(getModListHash());
    }

    /* ------------------------------------------------------------ */

    private int getOrCreateId(String hash) {
        for (Map.Entry<Integer, String> e : versions.entrySet()) {
            if (e.getValue()
                .equals(hash)) {
                return e.getKey();
            }
        }

        int id = nextId++;
        versions.put(id, hash);
        markDirty();
        return id;
    }

    // Save and load NBT.

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nextId = nbt.getInteger("NextId");

        versions.clear();
        NBTTagCompound tag = nbt.getCompoundTag("Versions");

        for (String key : tag.func_150296_c()) {
            versions.put(Integer.parseInt(key), tag.getString(key));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("NextId", nextId);

        NBTTagCompound tag = new NBTTagCompound();
        for (Map.Entry<Integer, String> e : versions.entrySet()) {
            tag.setString(Integer.toString(e.getKey()), e.getValue());
        }

        nbt.setTag("Versions", tag);
    }

    private static String getModListHash() {

        List<String> entries = new ArrayList<>();

        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            entries.add(mod.getModId() + ":" + mod.getVersion());
        }

        // Ensure stable ordering regardless of load order
        Collections.sort(entries);

        // Join with an unambiguous delimiter
        StringBuilder sb = new StringBuilder();
        for (String entry : entries) {
            sb.append(entry)
                .append('|');
        }

        return sb.toString();
    }
}
