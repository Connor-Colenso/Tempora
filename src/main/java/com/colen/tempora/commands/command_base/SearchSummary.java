package com.colen.tempora.commands.command_base;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.ChatComponentWrappedTranslation;
import com.colen.tempora.chat.EventChat;

// Line under a lookup's results saying what was searched, e.g. "Searched r:10 t:3d l:all p:anyone n:5", so filters left
// out aren't a mystery. Defaults are grey and typed ones green. Hovering a filter shows what it does and either that
// it's the default or which default it overrides
public final class SearchSummary {

    private final TemporaSubcommand command;
    private final CommandFilters filters;
    private final IChatComponent filterList = new ChatComponentText("");

    public SearchSummary(TemporaSubcommand command, CommandFilters filters) {
        this.command = command;
        this.filters = filters;
    }

    // A filter as typed, or with shownDefault (e.g. "10" or "all") if it was left out
    public SearchSummary add(String key, String shownDefault) {
        String typed = filters.get(key);
        // Typing the default (e.g. s:logger) still counts as default, not an override
        boolean isDefault = typed == null || typed.equalsIgnoreCase(shownDefault);

        // Under the filter, whether it was left at default (red) or what it was given instead (green like the filter),
        // then what it does
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

    // Whole numbers (e.g. "1000") as a number to format on the client, anything else (e.g. "3d") as is
    private static Object numberOrText(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    // "Searched ..." under the results, or "Nothing found ..." if there were none
    public IChatComponent line(boolean foundAny) {
        IChatComponent line = new ChatComponentTranslation(
            foundAny ? "tempora.command.search.searched" : "tempora.command.search.nothing_found",
            filterList);
        line.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return line;
    }
}
