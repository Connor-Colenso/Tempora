package com.colen.tempora.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.utils.StackTraceUtils;

// Client only, kept separate so a dedicated server never loads the client classes it uses
final class ClientStackTracePrinter {

    private ClientStackTracePrinter() {}

    // Prints a whole stack trace in the player's own chat under a header, deepest call first
    static void print(String trace) {
        GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();

        IChatComponent header = new ChatComponentTranslation(
            "====== %s ======",
            new ChatComponentTranslation("tempora.trace.full"));
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        chat.printChatMessage(header);

        for (IChatComponent line : StackTraceUtils.toChatLines(trace)) {
            chat.printChatMessage(line);
        }
    }
}
