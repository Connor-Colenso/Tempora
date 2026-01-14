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
        // Base text component that appears in chat
        ChatComponentText component = new ChatComponentText(displayText);
        IChatComponent hoverComponent = new ChatComponentText(hoverText);
        hoverComponent.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Style for hover and click events
        component.setChatStyle(
            component.getChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent))
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText)));

        return component;
    }
}
