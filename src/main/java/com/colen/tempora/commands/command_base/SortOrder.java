package com.colen.tempora.commands.command_base;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

/**
 * How a lookup's s: filter lays out its results: under each logger's own header, or all together in the order they
 * happened.
 */
public enum SortOrder {

    // The latest n of each logger, under its own header.
    LOGGER,
    // The latest n of all of them together, in the order they happened.
    TIME;

    public static final String DEFAULT = "logger";

    // As typed in s:, e.g. "time".
    public String typed() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> choices() {
        List<String> choices = new ArrayList<>();
        for (SortOrder order : values()) choices.add(order.typed());
        return choices;
    }

    // Null, after telling the sender the choices, if typed isn't one of them.
    public static @Nullable SortOrder parse(ICommandSender sender, String typed) {
        for (SortOrder order : values()) {
            if (order.typed()
                .equalsIgnoreCase(typed)) return order;
        }

        IChatComponent error = new ChatComponentTranslation(
            "tempora.command.bad_sort",
            typed,
            String.join(", ", choices()));
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(error);
        return null;
    }
}
