package com.colen.tempora.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Immutable record of where a container GUI was opened. */
public final class LastInvPos {

    public final int dimId;
    public final int x;
    public final int y;
    public final int z;

    public LastInvPos(int dimId, int x, int y, int z) {
        this.dimId = dimId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return String.format("D%d (%d,%d,%d)", dimId, x, y, z);
    }

    public static final ConcurrentMap<UUID, LastInvPos> LAST_OPENED = new ConcurrentHashMap<>();

    public static @Nullable TileEntity getTileEntity(UUID uuid) {
        LastInvPos pos = LAST_OPENED.get(uuid);
        World w = MinecraftServer.getServer()
            .worldServerForDimension(pos.dimId);

        return w.getTileEntity(pos.x, pos.y, pos.z);
    }
}
