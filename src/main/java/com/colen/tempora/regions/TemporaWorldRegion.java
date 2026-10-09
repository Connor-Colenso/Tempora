package com.colen.tempora.regions;

import static com.colen.tempora.utils.ChatUtils.createHoverableClickable;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.PlayerUtils.playerNameFromUUID;
import static com.colen.tempora.utils.TimeUtils.formatTime;

import java.awt.Color;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.colen.tempora.utils.CommandUtils;

public class TemporaWorldRegion {

    public static final double BLOCK_EDGE_EPSILON = 0.002;

    private static final Random RANDOM = new Random();

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

    // Keyed by logger name rather than the logger itself, so a rule for a logger that is disabled or no longer
    // exists survives a save/load instead of being dropped. Sorted so rules always display in the same order, and
    // concurrent because loggers read it from whichever thread fired the event.
    private final Map<String, LogRule> loggerRules = new ConcurrentSkipListMap<>();

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

        // Random hue, but always vivid so the region stands out when rendered.
        this.color = Color.getHSBColor(RANDOM.nextFloat(), 0.9f, 1.0f);
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

    /* ---------- Logger rules ---------- */

    public @Nullable LogRule getRule(String loggerName) {
        return loggerRules.get(loggerName);
    }

    public Map<String, LogRule> getRules() {
        return Collections.unmodifiableMap(loggerRules);
    }

    // Use TemporaRegionRegistry.setRule/clearRule for regions in the registry, so the change is saved.
    void putRule(String loggerName, LogRule rule) {
        loggerRules.put(loggerName, rule);
    }

    boolean removeRule(String loggerName) {
        return loggerRules.remove(loggerName) != null;
    }

    /* ---------- Helpers ---------- */

    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    // Max bounds are exclusive, so a region covering blocks 0 to 4 has maxX = 5.
    public final boolean contains(int dim, double x, double y, double z) {
        return this.dimID == dim && x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
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
        tag.setInteger("colorRed", color.getRed());
        tag.setInteger("colorGreen", color.getGreen());
        tag.setInteger("colorBlue", color.getBlue());

        NBTTagCompound rulesTag = new NBTTagCompound();
        for (Map.Entry<String, LogRule> rule : loggerRules.entrySet()) {
            rulesTag.setString(
                rule.getKey(),
                rule.getValue()
                    .name());
        }
        tag.setTag("loggerRules", rulesTag);

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
        // Only real regions are saved, wand highlights are client side only.
        region.renderMode = RegionRenderMode.REGION;

        int colorRed = tag.getInteger("colorRed");
        int colorGreen = tag.getInteger("colorGreen");
        int colorBlue = tag.getInteger("colorBlue");
        region.color = new Color(colorRed, colorGreen, colorBlue);

        NBTTagCompound rulesTag = tag.getCompoundTag("loggerRules");
        for (String loggerName : rulesTag.func_150296_c()) {
            LogRule rule = LogRule.fromString(rulesTag.getString(loggerName));
            if (rule != null) {
                region.loggerRules.put(loggerName, rule);
            }
        }

        return region;
    }

    /* ---------- Chat ---------- */

    // Clickable corner blocks, matching the inclusive coordinates typed into /tempora region create.
    public IChatComponent getMinCornerChatComponent() {
        return teleportChatComponent(minX, minY, minZ, dimID, CommandUtils.TeleportType.BLOCK);
    }

    public IChatComponent getMaxCornerChatComponent() {
        return teleportChatComponent(maxX - 1, maxY - 1, maxZ - 1, dimID, CommandUtils.TeleportType.BLOCK);
    }

    public IChatComponent getMidpointChatComponent() {
        return teleportChatComponent(getMidX(), getMidY(), getMidZ(), dimID, CommandUtils.TeleportType.EXACT);
    }

    public IChatComponent getTeleportToRegionChatComponent() {
        ChatComponentTranslation line = new ChatComponentTranslation(
            "tempora.command.region.list.entry",
            createHoverableClickable(label, regionUUID),
            getMinCornerChatComponent(),
            getMaxCornerChatComponent(),
            getMidpointChatComponent(),
            formatTime(regionOriginTimeMs),
            playerNameFromUUID(playerAuthorUUID));

        line.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);

        return line;
    }

    // E.g. "Rules: block ON, death OFF".
    public IChatComponent getRulesChatComponent() {
        if (loggerRules.isEmpty()) {
            IChatComponent none = new ChatComponentTranslation("tempora.region.rules.none");
            none.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            return none;
        }

        IChatComponent rules = new ChatComponentText("");
        boolean first = true;
        for (Map.Entry<String, LogRule> rule : loggerRules.entrySet()) {
            if (!first) rules.appendSibling(new ChatComponentText(", "));
            first = false;

            rules.appendSibling(new ChatComponentText(TemporaLoggerManager.displayName(rule.getKey()) + " "));
            rules.appendSibling(ruleChatComponent(rule.getValue()));
        }

        IChatComponent line = new ChatComponentTranslation("tempora.region.rules", rules);
        line.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return line;
    }

    public static IChatComponent ruleChatComponent(LogRule rule) {
        IChatComponent component = new ChatComponentText(rule.name());
        component.getChatStyle()
            .setColor(rule == LogRule.ON ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);
        return component;
    }
}
