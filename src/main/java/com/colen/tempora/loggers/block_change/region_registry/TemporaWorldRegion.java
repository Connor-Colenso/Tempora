package com.colen.tempora.loggers.block_change.region_registry;

import static com.colen.tempora.rendering.RenderUtils.getRandomBrightColor;
import static com.colen.tempora.utils.ChatUtils.createHoverableClickable;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.PlayerUtils.playerNameFromUUID;
import static com.colen.tempora.utils.TimeUtils.formatTime;

import java.awt.Color;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.rendering.regions.RegionRenderMode;

public class TemporaWorldRegion {

    public static final double BLOCK_EDGE_EPSILON = 0.002;

    private final int dimID;
    private final double minX, minY, minZ;
    private final double maxX, maxY, maxZ;

    private long renderStartTimeMs; // when this object should start rendering
    private long regionOriginTimeMs; // when the region itself was defined
    private String regionUUID;
    private String playerAuthorUUID;
    private Color color;
    private RegionRenderMode renderMode;
    private String label;

    // Todo introduce regions for other concepts, such as allowing/disallowing other loggers.
    // This could be useful for stopping it logging e.g. mob deaths in a mob farm.

    /**
     * Constructor: defines the bounding box coordinates only (x1,y1,z1 -> x2,y2,z2).
     * The rest should be set via setters.
     */
    public TemporaWorldRegion(int dimID, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.dimID = dimID;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);

        this.color = getRandomBrightColor();
    }

    /* ---------- Getters / Setters ---------- */

    public int getDimID() {
        return dimID;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public double getMidX() {
        return (minX + maxX) / 2.0;
    }

    public double getMidY() {
        return (minY + maxY) / 2.0;
    }

    public double getMidZ() {
        return (minZ + maxZ) / 2.0;
    }

    public long getRenderStartTimeMs() {
        return renderStartTimeMs;
    }

    public void setRenderStartTimeMs(long renderStartTimeMs) {
        this.renderStartTimeMs = renderStartTimeMs;
    }

    public long getRegionOriginTimeMs() {
        return regionOriginTimeMs;
    }

    public void setRegionOriginTimeMs(long regionOriginTimeMs) {
        this.regionOriginTimeMs = regionOriginTimeMs;
    }

    public String getRegionUUID() {
        return regionUUID;
    }

    public void setRegionUUID(String regionUUID) {
        this.regionUUID = regionUUID;
    }

    public String getPlayerAuthorUUID() {
        return playerAuthorUUID;
    }

    public void setPlayerAuthorUUID(String playerAuthorUUID) {
        this.playerAuthorUUID = playerAuthorUUID;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public RegionRenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RegionRenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /* ---------- Helpers ---------- */

    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    public boolean containsBlock(int dim, Number x, Number y, Number z) {
        if (this.dimID != dim) return false;

        double dx = x.doubleValue();
        double dy = y.doubleValue();
        double dz = z.doubleValue();

        return dx >= minX && dx < maxX && dy >= minY && dy < maxY && dz >= minZ && dz < maxZ;
    }

    public boolean intersectsWith(int dim, AxisAlignedBB box) {
        if (this.dimID != dim) return false;

        // Create an AABB representing this region
        AxisAlignedBB regionAABB = AxisAlignedBB
            .getBoundingBox(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);

        // Use Minecraft's built-in intersect check
        return regionAABB.intersectsWith(box);
    }

    /* ---------- NBT Serialization ---------- */

    public NBTTagCompound writeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", dimID);
        tag.setDouble("minX", minX);
        tag.setDouble("minY", minY);
        tag.setDouble("minZ", minZ);
        tag.setDouble("maxX", maxX);
        tag.setDouble("maxY", maxY);
        tag.setDouble("maxZ", maxZ);
        tag.setString("label", label);
        tag.setLong("regionOriginTimeMs", regionOriginTimeMs);
        tag.setString("regionUUID", regionUUID);
        tag.setString("playerAuthorUUID", playerAuthorUUID);
        tag.setString("renderMode", renderMode.toString());
        tag.setInteger("colorRed", color.getRed());
        tag.setInteger("colorGreen", color.getGreen());
        tag.setInteger("colorBlue", color.getBlue());
        return tag;
    }

    public static TemporaWorldRegion readNBT(NBTTagCompound tag) {
        TemporaWorldRegion region = new TemporaWorldRegion(
            tag.getInteger("dim"),
            tag.getDouble("minX"),
            tag.getDouble("minY"),
            tag.getDouble("minZ"),
            tag.getDouble("maxX"),
            tag.getDouble("maxY"),
            tag.getDouble("maxZ"));

        region.label = tag.getString("label");
        region.regionOriginTimeMs = tag.getLong("regionOriginTimeMs");
        region.regionUUID = tag.getString("regionUUID");
        region.playerAuthorUUID = tag.getString("playerAuthorUUID");
        region.renderMode = RegionRenderMode.valueOf(tag.getString("renderMode"));

        int colorRed = tag.getInteger("colorRed");
        int colorGreen = tag.getInteger("colorGreen");
        int colorBlue = tag.getInteger("colorBlue");
        region.color = new Color(colorRed, colorGreen, colorBlue);

        return region;
    }

    public IChatComponent getChatComponent() {
        // Centre of the region for a sensible teleport target
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double cz = (minZ + maxZ) / 2.0;

        /* Clickable coordinate component */
        ChatComponentTranslation tp = (ChatComponentTranslation) teleportChatComponent(cx, cy, cz, dimID);

        /* Whole entry line */
        ChatComponentTranslation line = new ChatComponentTranslation(
            "tempora.command.list_regions.entry",
            createHoverableClickable(label, regionUUID),
            teleportChatComponent(minX, minY, minZ, dimID),
            teleportChatComponent(maxX, maxY, maxZ, dimID),
            tp,
            formatTime(regionOriginTimeMs),
            playerNameFromUUID(playerAuthorUUID));

        line.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);

        return line;
    }
}
