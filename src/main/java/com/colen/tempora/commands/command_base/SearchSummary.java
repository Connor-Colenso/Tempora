package com.colen.tempora.commands.command_base;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.ChatComponentWrappedTranslation;
import com.colen.tempora.chat.EventChat;

/**
 * The line under a lookup's results saying what was searched, e.g. "Searched r:10 t:3d l:all p:anyone n:5", so the
 * filters left out aren't a mystery. Filters left at their default are grey and the ones typed green. Hovering over a
 * filter shows what it does, and either that it is the default or which default it overrides.
 */
public final class SearchSummary {

    private final TemporaSubcommand command;
    private final CommandFilters filters;
    private final IChatComponent filterList = new ChatComponentText("");

    public SearchSummary(TemporaSubcommand command, CommandFilters filters) {
        this.command = command;
        this.filters = filters;
    }

    // A filter as typed, or with shownDefault, e.g. "10" or "all", if it was left out.
    public SearchSummary add(String key, String shownDefault) {
        String typed = filters.get(key);
        boolean isDefault = typed == null;

        // Under the filter, whether it was left at its default, in red, or what it was given instead, in green like
        // the filter, then what it does.
        String shown = key + ":" + (isDefault ? shownDefault : typed);
        EnumChatFormatting valueColour = isDefault ? EnumChatFormatting.RED : EnumChatFormatting.GREEN;
        IChatComponent value = isDefault
            ? new ChatComponentWrappedTranslation("tempora.command.search.default", numberOrText(shownDefault))
            : new ChatComponentWrappedTranslation(
                "tempora.command.search.overridden",
                numberOrText(shownDefault),
                numberOrText(typed));
        value.getChatStyle()
            .setColor(valueColour);
        IChatComponent hover = EventChat.titledHover(value, command.getFilterDescription(key));

        IChatComponent filter = new ChatComponentText(shown);
        filter.getChatStyle()
            .setColor(isDefault ? EnumChatFormatting.GRAY : EnumChatFormatting.GREEN)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));

        if (!filterList.getSiblings()
            .isEmpty()) filterList.appendSibling(new ChatComponentText(" "));
        filterList.appendSibling(filter);
        return this;
    }

    // A whole number, e.g. "1000", as a number to format on the client, anything else, e.g. "3d", as it is.
    private static Object numberOrText(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    // "Searched ..." to go under the results, or "Nothing found ..." if there were none.
    public IChatComponent line(boolean foundAny) {
        IChatComponent line = new ChatComponentTranslation(
            foundAny ? "tempora.command.search.searched" : "tempora.command.search.nothing_found",
            filterList);
        line.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return line;
    }
}
