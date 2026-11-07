package com.colen.tempora.chat;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.colen.tempora.utils.nbt.NBTUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

/**
 * Bits every event's chat line is built from so all loggers read the same. When (dark grey, date on hover), who
 * (yellow), what (white), where (aqua, click to teleport), joined by grey words from the lang file. Stuff that would
 * make a line wrap, like IDs and UUIDs, goes in hovers
 */
public final class EventChat {

    // Chat packets can't carry huge hover text, so big NBT (full backpacks, AE2 cells) isn't shown. Measured in bytes
    // as sent since the JSON turns chars like < into six byte escapes
    private static final int MAX_HOVER_NBT_BYTES = 16384;

    private EventChat() {}

    /** Whole line. The translation's own words are grey, args keep the colours given below */
    public static IChatComponent line(String key, Object... args) {
        IChatComponent line = new ChatComponentTranslation(key, args);
        line.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return line;
    }

    /** How long ago, e.g. "6.4m ago", with the date and time on hover */
    public static IChatComponent time(long timestamp) {
        IChatComponent time = TimeUtils.formatTime(timestamp);
        time.getChatStyle()
            .setColor(EnumChatFormatting.DARK_GRAY);
        return time;
    }

    /** Player from their UUID with the UUID on hover. Causes like tempora.unknown.player get translated */
    public static IChatComponent player(@Nullable String uuid) {
        IChatComponent name = PlayerUtils.playerNameFromUUID(uuid);
        name.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        return name;
    }

    /**
     * Nearest player to an event with no known player (like a block change), with how near on hover. Might've had
     * nothing to do with it
     */
    public static IChatComponent closestPlayer(@Nullable String uuid, double distance) {
        IChatComponent name = player(uuid);
        name.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    grey(
                        new ChatComponentTranslation(
                            "tempora.chat.closest_player",
                            formatNumber(Math.round(distance))))));
        return name;
    }

    /** Block or item named on the client (lots of mods only name their items there), with ID and meta on hover */
    public static IChatComponent block(int id, int metadata) {
        return block(id, metadata, id, metadata);
    }

    /** Block named by its pick block item (what players know it as), with the block's own ID and meta on hover */
    public static IChatComponent block(int pickID, int pickMetadata, int blockID, int blockMetadata) {
        IChatComponent name = new ChatComponentBlockName(pickID, pickMetadata);
        IChatComponent hover = new ChatComponentTranslation(
            "tempora.chat.id",
            new ChatComponentNumber(blockID),
            new ChatComponentNumber(blockMetadata));
        name.getChatStyle()
            .setColor(EnumChatFormatting.WHITE)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, grey(hover)));
        return name;
    }

    /**
     * Item someone held or moved, named on the server like vanilla's own item mentions, with its tooltip on hover. NBT
     * stored by {@link NBTUtils#getEncodedItemNBT} goes in the tooltip unless it's too big to send
     */
    public static IChatComponent item(int itemID, int metadata, String encodedNBT) {
        Item item = Item.getItemById(itemID);
        if (item == null) {
            return text(
                "[unknown item " + formatNumber(itemID) + ":" + formatNumber(metadata) + "]",
                EnumChatFormatting.WHITE);
        }

        ItemStack itemStack = new ItemStack(item, 1, metadata);
        NBTTagCompound tag = NBTUtils.decodeItemNBT(encodedNBT);
        if (tag != null) itemStack.setTagCompound(tag);

        IChatComponent name = text(itemStack.getDisplayName(), EnumChatFormatting.WHITE);

        IChatComponent hoverNBT = new ChatComponentText(
            itemStack.writeToNBT(new NBTTagCompound())
                .toString());
        if (IChatComponent.Serializer.func_150696_a(hoverNBT)
            .getBytes(StandardCharsets.UTF_8).length <= MAX_HOVER_NBT_BYTES) {
            name.getChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverNBT));
        }

        return name;
    }

    /**
     * Entity from its EntityList name (e.g. "CaveSpider"). With a UUID it's shown on hover and clicking puts it in the
     * chat box to copy
     */
    public static IChatComponent entity(String registryName, @Nullable String uuid) {
        IChatComponent name = entityName(registryName);
        if (uuid == null) return name;

        // White, a lone UUID hover (a player's) gets drawn as a white title in GTNH
        IChatComponent title = new ChatComponentTranslation("tempora.uuid.display", uuid);
        title.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        IChatComponent hover = titledHover(title, new ChatComponentTranslation("tempora.uuid.click_to_copy"));

        name.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));
        return name;
    }

    /**
     * Entity name from its EntityList name. Names with no translation show as stored, that's entities from mods that
     * don't name them and old rows that hold the display name
     */
    public static IChatComponent entityName(String registryName) {
        String key = "entity." + registryName + ".name";
        IChatComponent name = StatCollector.canTranslate(key) ? new ChatComponentTranslation(key)
            : new ChatComponentText(registryName);
        name.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        return name;
    }

    /** Where an event happened, as clickable coords */
    public static IChatComponent where(PositionalEventInfo event, CommandUtils.TeleportType type) {
        return CommandUtils.teleportChatComponent(event.x, event.y, event.z, event.dimensionID, type);
    }

    /** Text shown as is, e.g. a typed command. Plain text not a translation so a % in it can't break the line */
    public static IChatComponent text(String text, EnumChatFormatting colour) {
        IChatComponent component = new ChatComponentText(text);
        component.getChatStyle()
            .setColor(colour);
        return component;
    }

    public static IChatComponent grey(IChatComponent component) {
        component.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return component;
    }

    /**
     * Hover of several lines under a title, the title needs a colour other than grey. Each line is grey unless it has
     * its own colour
     * <p>
     * GTNH draws hovers with Chromatic Tooltips, which makes the first line the title and turns it white unless it
     * starts with a non grey colour. So a hover starting with grey text, or anything else first (even a reset), shows
     * its first line white and the rest grey, e.g. half a wrapped description. Here the title is the hover's root so
     * its colour comes first and sticks
     */
    public static IChatComponent titledHover(IChatComponent title, IChatComponent... lines) {
        EnumChatFormatting colour = title.getChatStyle()
            .getColor();
        if (colour == null || colour == EnumChatFormatting.GRAY) {
            throw new IllegalArgumentException("A hover's title needs a colour other than grey, or it is drawn white.");
        }

        for (IChatComponent line : lines) {
            // Before appending, while an unset colour still reads as null and not the title's
            if (line.getChatStyle()
                .getColor() == null) grey(line);
            title.appendText("\n")
                .appendSibling(line);
        }
        return title;
    }
}
