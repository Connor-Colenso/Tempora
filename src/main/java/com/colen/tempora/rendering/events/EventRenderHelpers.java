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

import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Small helpers shared by event renderers. */
@SideOnly(Side.CLIENT)
public final class EventRenderHelpers {

    // Height of one line of RenderUtils.renderFloatingText, in blocks.
    public static final double TEXT_LINE_HEIGHT = 9 * 0.025;

    private EventRenderHelpers() {}

    public static ChunkPosition blockOf(PositionalEventInfo event) {
        return new ChunkPosition(
            MathHelper.floor_double(event.x),
            MathHelper.floor_double(event.y),
            MathHelper.floor_double(event.z));
    }

    /**
     * Keeps only the newest event at each block, in the order given. Several events at one block would draw on top of
     * each other.
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
     * Groups events by the given key, groups in the order their first event appears (so still farthest first), each
     * group oldest first.
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

    /** Draws lines of text with their bottom edge at the given height, so they sit on top of what is below. */
    public static void renderLabelAbove(List<String> lines, double x, double bottomY, double z) {
        RenderUtils.renderFloatingText(lines, x, bottomY + lines.size() * TEXT_LINE_HEIGHT / 2, z);
    }

    /**
     * Joins events, oldest first, with a line that brightens towards the newest, so the direction of travel shows. In
     * world coordinates, the camera offset already applied.
     */
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
                // Just above the ground, so it is not hidden in it.
                tes.addVertex(event.x, event.y + 0.1, event.z);
            }
            tes.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** E.g. "5 minutes ago". */
    public static String timeAgo(long timestamp) {
        TimeUtils.DurationParts parts = TimeUtils.relativeTimeAgoFormatter(timestamp);
        return StatCollector
            .translateToLocalFormatted(parts.translationKey, NumberFormatUtil.formatNumber(parts.time, ONE_DP));
    }

    /**
     * A player's name from their UUID. Anything that is not a UUID (a cause such as "[EntityZombie]", or a translation
     * key such as tempora.unknown.player) is shown as it is, translated if it can be.
     */
    public static String playerName(@Nullable String uuidOrCause) {
        if (uuidOrCause == null) return "?";

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidOrCause);
        } catch (IllegalArgumentException notAUUID) {
            return StatCollector.translateToLocal(uuidOrCause);
        }

        // Only filled in where the server runs, so in singleplayer.
        String cached = UsernameCache.getLastKnownUsername(uuid);
        if (cached != null) return cached;

        EntityPlayer online = onlinePlayer(uuid);
        if (online != null) return online.getCommandSenderName();

        return StatCollector.translateToLocal(PlayerUtils.UNKNOWN_PLAYER_NAME);
    }

    /** The player with this UUID if they are near enough for this client to see them, else null. */
    public static @Nullable EntityPlayer onlinePlayer(UUID uuid) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? null : mc.theWorld.func_152378_a(uuid);
    }

    /** An entity's name from the name it is registered under in EntityList, e.g. "Cave Spider" for "CaveSpider". */
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

        // Some mods' items throw for metadata they do not expect.
        try {
            return stack.getDisplayName();
        } catch (RuntimeException e) {
            return itemID + ":" + metadata;
        }
    }
}
