package com.colen.tempora.rendering.events;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.UsernameCache;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.loggers.entity_death.DeathCause;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Small helpers shared by event renderers */
@SideOnly(Side.CLIENT)
public final class EventRenderHelpers {

    // Height of one line of RenderUtils.renderFloatingText, in blocks
    public static final double TEXT_LINE_HEIGHT = 9 * 0.025;

    private EventRenderHelpers() {}

    public static ChunkPosition blockOf(PositionalEventInfo event) {
        return new ChunkPosition(
            MathHelper.floor_double(event.x),
            MathHelper.floor_double(event.y),
            MathHelper.floor_double(event.z));
    }

    /**
     * Keeps only the newest event at each block, in the order given. Several at one block would draw on top of each
     * other
     */
    public static <E extends PositionalEventInfo> List<E> latestPerBlock(List<E> events) {
        Map<ChunkPosition, E> newest = new HashMap<>();
        for (E event : events) {
            newest.merge(blockOf(event), event, (a, b) -> b.timestamp > a.timestamp ? b : a);
        }

        List<E> kept = new ArrayList<>(newest.size());
        for (E event : events) {
            if (newest.get(blockOf(event)) == event) kept.add(event);
        }
        return kept;
    }

    /**
     * Groups events by key. Groups in the order their first event shows up (so still farthest first), each group oldest
     * first
     */
    public static <E extends PositionalEventInfo, K> List<List<E>> groupBy(List<E> events, Function<E, K> key) {
        Map<K, List<E>> groups = new LinkedHashMap<>();
        for (E event : events) {
            groups.computeIfAbsent(key.apply(event), k -> new ArrayList<>())
                .add(event);
        }

        List<List<E>> result = new ArrayList<>(groups.values());
        for (List<E> group : result) {
            group.sort(Comparator.comparingLong(event -> event.timestamp));
        }
        return result;
    }

    /** Draws text with its bottom edge at the given height so it sits on top of whatever's below */
    public static void renderLabelAbove(List<String> lines, double x, double bottomY, double z) {
        RenderUtils.renderFloatingText(lines, x, bottomY + lines.size() * TEXT_LINE_HEIGHT / 2, z);
    }

    /**
     * Draws an arrow from a point, {@code length} blocks long, pointing the way a player with this yaw and pitch
     * (degrees, as MC stores them) was looking
     */
    public static void renderLookArrow(double x, double y, double z, float yaw, float pitch, double length,
        Color colour) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        // Same direction vanilla throws an item, before its little upward nudge and random spread
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double tipX = x + dirX * length;
        double tipY = y + dirY * length;
        double tipZ = z + dirZ * length;

        // The head's two barbs go back from the tip and out each side, level with the ground
        double back = 0.25;
        double side = 0.15;
        double sideX = Math.cos(yawRad);
        double sideZ = Math.sin(yawRad);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(3F);

            Tessellator tes = Tessellator.instance;
            tes.startDrawing(GL11.GL_LINES);
            tes.setColorRGBA_I(colour.getRGB() & 0xFFFFFF, 255);
            tes.addVertex(x, y, z);
            tes.addVertex(tipX, tipY, tipZ);
            for (int sign = -1; sign <= 1; sign += 2) {
                tes.addVertex(tipX, tipY, tipZ);
                tes.addVertex(
                    tipX - dirX * back + sideX * side * sign,
                    tipY - dirY * back,
                    tipZ - dirZ * back + sideZ * side * sign);
            }
            tes.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void renderPath(List<? extends PositionalEventInfo> oldestFirst, Color colour) {
        if (oldestFirst.size() < 2) return;

        float red = colour.getRed() / 255f;
        float green = colour.getGreen() / 255f;
        float blue = colour.getBlue() / 255f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(3F);

            Tessellator tes = Tessellator.instance;
            tes.startDrawing(GL11.GL_LINE_STRIP);
            int last = oldestFirst.size() - 1;
            for (int i = 0; i <= last; i++) {
                PositionalEventInfo event = oldestFirst.get(i);
                tes.setColorRGBA_F(red, green, blue, 0.25f + 0.75f * i / last);
                // Just above the ground so it isn't hidden in it
                tes.addVertex(event.x, event.y + 0.1, event.z);
            }
            tes.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** E.g. "5 minutes ago" */
    public static String timeAgo(long timestamp) {
        TimeUtils.DurationParts parts = TimeUtils.relativeTimeAgoFormatter(timestamp);
        return StatCollector
            .translateToLocalFormatted(parts.translationKey, NumberFormatUtil.formatNumber(parts.time, ONE_DP));
    }

    /**
     * Player name from their UUID. Anything that isn't a UUID (a key like tempora.unknown.player) is shown as is,
     * translated if it can be
     */
    public static String playerName(@Nullable String uuidOrCause) {
        if (uuidOrCause == null) return "?";

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidOrCause);
        } catch (IllegalArgumentException notAUUID) {
            return StatCollector.translateToLocal(uuidOrCause);
        }

        // Only filled in where the server runs, so singleplayer
        String cached = UsernameCache.getLastKnownUsername(uuid);
        if (cached != null) return cached;

        EntityPlayer online = onlinePlayer(uuid);
        if (online != null) return online.getCommandSenderName();

        return StatCollector.translateToLocal(PlayerUtils.UNKNOWN_PLAYER_NAME);
    }

    /**
     * What killed something, from the death loggers' columns (see DeathCause). The player or mob if one did,
     * otherwise how it died
     */
    public static String killerName(String killerPlayerUUID, String killerMob, String damageType) {
        if (PlayerUtils.isUUID(killerPlayerUUID)) return playerName(killerPlayerUUID);
        if (!killerMob.isEmpty()) return entityName(killerMob);
        if (!damageType.isEmpty()) return DeathCause.damageText(damageType);
        return StatCollector.translateToLocal(PlayerUtils.UNKNOWN_CAUSE);
    }

    /** Player with this UUID if they're close enough for this client to see, otherwise null */
    public static @Nullable EntityPlayer onlinePlayer(UUID uuid) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? null : mc.theWorld.func_152378_a(uuid);
    }

    /** Entity name from its EntityList name, e.g. "Cave Spider" for "CaveSpider" */
    public static String entityName(@Nullable String entityListName) {
        if (entityListName == null) return "?";

        String key = "entity." + entityListName + ".name";
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : entityListName;
    }

    public static @Nullable ItemStack itemStack(int itemID, int metadata) {
        Item item = Item.getItemById(itemID);
        return item == null ? null : new ItemStack(item, 1, metadata);
    }

    public static String itemName(int itemID, int metadata) {
        ItemStack stack = itemStack(itemID, metadata);
        if (stack == null) return itemID + ":" + metadata;

        // Some mods' items throw for meta they don't expect
        try {
            return stack.getDisplayName();
        } catch (RuntimeException e) {
            return itemID + ":" + metadata;
        }
    }
}
