package com.colen.tempora.loggers.block_change.region_registry;

import static com.colen.tempora.rendering.RenderUtils.getRandomBrightColor;

import java.awt.Color;

import net.minecraft.nbt.NBTTagCompound;

import com.colen.tempora.rendering.regions.RegionRenderMode;

public final class RegionToRender {

    public static final double BLOCK_EDGE_EPSILON = 0.002;

    public final int dim;
    public final double minX, minY, minZ;
    public final double maxX, maxY, maxZ;
    public final long createdAtMs;
    public final String uuid;
    public final Color color;
    public final RegionRenderMode renderMode;

    public RegionToRender(
            int dim,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            long createdAtMs,
            String uuid,
            RegionRenderMode renderMode
    ) {
        this.dim = dim;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.createdAtMs = createdAtMs;
        this.uuid = uuid;
        this.renderMode = renderMode;
        this.color = getRandomBrightColor();
    }

    public boolean isExpired(long cutoffMs) {
        return createdAtMs < cutoffMs;
    }

    /* ---------- NBT ---------- */

    public NBTTagCompound writeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", dim);
        tag.setDouble("minX", minX);
        tag.setDouble("minY", minY);
        tag.setDouble("minZ", minZ);
        tag.setDouble("maxX", maxX);
        tag.setDouble("maxY", maxY);
        tag.setDouble("maxZ", maxZ);
        tag.setLong("createdAtMs", createdAtMs);
        tag.setString("uuid", uuid);
        tag.setString("renderMode", renderMode.name());
        return tag;
    }

    public static RegionToRender readNBT(NBTTagCompound tag) {
        return new RegionToRender(
                tag.getInteger("dim"),
                tag.getDouble("minX"),
                tag.getDouble("minY"),
                tag.getDouble("minZ"),
                tag.getDouble("maxX"),
                tag.getDouble("maxY"),
                tag.getDouble("maxZ"),
                tag.getLong("createdAtMs"),
                tag.getString("uuid"),
                RegionRenderMode.valueOf(tag.getString("renderMode"))
        );
    }

    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    public boolean contains(int dim, Number x, Number y, Number z) {
        if (this.dim != dim) {
            return false;
        }

        double dx = x.doubleValue();
        double dy = y.doubleValue();
        double dz = z.doubleValue();

        return dx >= minX && dx < maxX && dy >= minY && dy < maxY && dz >= minZ && dz < maxZ;
    }

}



