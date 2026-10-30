package com.colen.tempora.commands.command_base;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.ChatComponentWrappedTranslation;

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

        // What the filter does, then whether it was left at its default, in red, or what it was given instead.
        IChatComponent description = new ChatComponentWrappedTranslation(command.getFilterLangKey(key));
        description.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        IChatComponent value = isDefault
            ? new ChatComponentWrappedTranslation("tempora.command.search.default", shownDefault)
            : new ChatComponentWrappedTranslation("tempora.command.search.overridden", shownDefault, typed);
        value.getChatStyle()
            .setColor(isDefault ? EnumChatFormatting.RED : EnumChatFormatting.GRAY);
        IChatComponent hover = new ChatComponentText("");
        hover.appendSibling(description)
            .appendSibling(new ChatComponentText("\n"))
            .appendSibling(value);

        IChatComponent filter = new ChatComponentText(key + ":" + (isDefault ? shownDefault : typed));
        filter.getChatStyle()
            .setColor(isDefault ? EnumChatFormatting.GRAY : EnumChatFormatting.GREEN)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));

        if (!filterList.getSiblings()
            .isEmpty()) filterList.appendSibling(new ChatComponentText(" "));
        filterList.appendSibling(filter);
        return this;
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
