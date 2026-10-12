package com.colen.tempora.chat;

import javax.annotation.Nullable;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

/**
 * The parts every event's chat line is built from, so all loggers' lines read alike: when (dark grey, the date on
 * hover), who (yellow), what (white), where (aqua, click to teleport), joined by grey words from the lang file. Details
 * that would make a line wrap, such as IDs and UUIDs, go in hovers.
 */
public final class EventChat {

    private EventChat() {}

    /** A whole line. The translation's own words are grey, its arguments keep the colours given below. */
    public static IChatComponent line(String key, Object... args) {
        IChatComponent line = new ChatComponentTranslation(key, args);
        line.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return line;
    }

    /** How long ago, e.g. "6.4m ago", with the date and time on hover. */
    public static IChatComponent time(long timestamp) {
        IChatComponent time = TimeUtils.formatTime(timestamp);
        time.getChatStyle()
            .setColor(EnumChatFormatting.DARK_GRAY);
        return time;
    }

    /** A player, from their UUID, with the UUID on hover. Causes such as tempora.unknown.player are translated. */
    public static IChatComponent player(@Nullable String uuid) {
        IChatComponent name = PlayerUtils.playerNameFromUUID(uuid);
        name.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        return name;
    }

    /**
     * A block or item, named on the client (many mods only name their items there), with its ID and metadata on
     * hover.
     */
    public static IChatComponent block(int id, int metadata) {
        return block(id, metadata, id, metadata);
    }

    /**
     * A block named by the item it drops when picked (what players know it as), with the block's own ID and metadata
     * on hover.
     */
    public static IChatComponent block(int pickID, int pickMetadata, int blockID, int blockMetadata) {
        IChatComponent name = new ChatComponentBlockName(pickID, pickMetadata);
        IChatComponent hover = new ChatComponentTranslation("tempora.chat.id", blockID, blockMetadata);
        name.getChatStyle()
            .setColor(EnumChatFormatting.WHITE)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, grey(hover)));
        return name;
    }

    /**
     * An entity, from the name it is registered under in EntityList (e.g. "CaveSpider"). With a UUID, it is shown on
     * hover, and clicking puts it in the chat box to copy.
     */
    public static IChatComponent entity(String registryName, @Nullable String uuid) {
        IChatComponent name = entityName(registryName);
        if (uuid == null) return name;

        IChatComponent hover = new ChatComponentTranslation("tempora.uuid.display", uuid);
        hover.appendText("\n")
            .appendSibling(new ChatComponentTranslation("tempora.uuid.click_to_copy"));

        name.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, grey(hover)))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));
        return name;
    }

    /**
     * An entity's name from its EntityList name. Names without a translation are shown as stored: entities from mods
     * that do not name them, and rows logged before names were stored this way, which hold the display name.
     */
    public static IChatComponent entityName(String registryName) {
        String key = "entity." + registryName + ".name";
        IChatComponent name = StatCollector.canTranslate(key) ? new ChatComponentTranslation(key)
            : new ChatComponentText(registryName);
        name.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        return name;
    }

    /** Where an event happened, as clickable coordinates. */
    public static IChatComponent where(PositionalEventInfo event, CommandUtils.TeleportType type) {
        return CommandUtils.teleportChatComponent(event.x, event.y, event.z, event.dimensionID, type);
    }

    /**
     * Text shown as it is, e.g. a command someone typed. Text rather than a translation, so a % in it cannot break the
     * line.
     */
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
}
