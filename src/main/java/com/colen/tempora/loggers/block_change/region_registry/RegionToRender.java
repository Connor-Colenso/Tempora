package com.colen.tempora.loggers.block_change.region_registry;

import static com.colen.tempora.rendering.RenderUtils.getRandomBrightColor;

import java.awt.Color;

import net.minecraft.nbt.NBTTagCompound;

public final class RegionToRender {

    public final int dim; // dimension id
    public final double minX, minY, minZ; // inclusive
    public final double maxX, maxY, maxZ; // inclusive
    public final long posPrintTime; // Time at which the event instance was made (in ms), used to track removal on
                                    // client side.

    public final Color color = getRandomBrightColor();

    public RegionToRender(int dim, double x1, double y1, double z1, double x2, double y2, double z2,
        long posPrintTime) {
        this.dim = dim;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.posPrintTime = posPrintTime;
    }

    public boolean contains(int dim, double x, double y, double z) {
        return this.dim == dim && x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    // NBT Save/load
    public NBTTagCompound writeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", dim);
        tag.setDouble("minX", minX);
        tag.setDouble("minY", minY);
        tag.setDouble("minZ", minZ);
        tag.setDouble("maxX", maxX);
        tag.setDouble("maxY", maxY);
        tag.setDouble("maxZ", maxZ);
        tag.setLong("posPrintTime", posPrintTime);
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
            tag.getLong("posPrintTime"));
    }

    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }
}
