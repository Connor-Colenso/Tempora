package com.colen.tempora.logging.loggers.block_change;

import net.minecraft.nbt.NBTTagCompound;

public final class IntRegion {

    public final int dim; // dimension id
    public final int minX, minY, minZ; // inclusive
    public final int maxX, maxY, maxZ; // inclusive
    public final long posPrintTime;

    public IntRegion(int dim, int x1, int y1, int z1, int x2, int y2, int z2, long posPrintTime) {
        this.dim = dim;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.posPrintTime = posPrintTime;
    }

    /** True if the block (x,y,z) in <em>this.dim</em> is inside the box. */
    public boolean contains(int dim, int x, int y, int z) {
        return this.dim == dim && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /* ---------- NBT helpers ---------- */

    public NBTTagCompound writeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", dim);
        tag.setInteger("minX", minX);
        tag.setInteger("minY", minY);
        tag.setInteger("minZ", minZ);
        tag.setInteger("maxX", maxX);
        tag.setInteger("maxY", maxY);
        tag.setInteger("maxZ", maxZ);
        tag.setLong("posPrintTime", posPrintTime);
        return tag;
    }

    public static IntRegion readNBT(NBTTagCompound tag) {
        return new IntRegion(
            tag.getInteger("dim"),
            tag.getInteger("minX"),
            tag.getInteger("minY"),
            tag.getInteger("minZ"),
            tag.getInteger("maxX"),
            tag.getInteger("maxY"),
            tag.getInteger("maxZ"),
            tag.getLong("posPrintTime"));
    }
}
