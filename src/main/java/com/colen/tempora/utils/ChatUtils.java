package com.colen.tempora.utils;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.gtnewhorizon.gtnhlib.util.numberformatting.options.FormatOptions;

public class ChatUtils {

    public static final FormatOptions ONE_DP = new FormatOptions().setDecimalPlaces(1);

    public static IChatComponent createHoverableClickable(String displayText, String hoverText) {
        ChatComponentText component = new ChatComponentText(displayText);
        IChatComponent hoverComponent = new ChatComponentText(hoverText);
        hoverComponent.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        component.setChatStyle(
            component.getChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent))
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText)));

        return component;
    }

    private static final int LINE_LENGTH = 53; // Total line length

    public static IChatComponent createCenteredLine(String text) {
        if (text == null) text = "";
        int textLength = text.length() + 2; // Plus a space either side
        if (textLength >= LINE_LENGTH) {
            // Too long, just yellow text without dashes
            ChatComponentText component = new ChatComponentText(text);
            component.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            return component;
        }

        int totalDashes = LINE_LENGTH - textLength;
        int dashesBefore = totalDashes / 2;
        int dashesAfter = totalDashes - dashesBefore;

        ChatComponentText before = new ChatComponentText(repeat("-", dashesBefore));
        before.getChatStyle()
            .setColor(EnumChatFormatting.DARK_GRAY);

        ChatComponentText middle = new ChatComponentText(" " + text + " ");
        middle.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);

        ChatComponentText after = new ChatComponentText(repeat("-", dashesAfter));
        after.getChatStyle()
            .setColor(EnumChatFormatting.DARK_GRAY);

        before.appendSibling(middle);
        before.appendSibling(after);

        return before;
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
