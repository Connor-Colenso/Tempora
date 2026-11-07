package com.colen.tempora.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

// Client only, kept separate so a dedicated server never loads the client classes it uses
final class ClientTextWrap {

    // Any narrower and a line only fits a few words, on a small window or big GUI scale
    private static final int MIN_WIDTH_PIXELS = 150;

    private ClientTextWrap() {}

    // Breaks text into lines no wider than 3/4 of the screen in GUI scaled pixels (how hovers are drawn), joined by
    // newlines which hovers show as lines. Measured every time so it follows window size and GUI scale
    static String wrapToHoverWidth(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        if (font == null) return text;

        int screenWidth = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledWidth();
        int width = Math.max(MIN_WIDTH_PIXELS, screenWidth * 3 / 4);
        return String.join("\n", font.listFormattedStringToWidth(text, width));
    }
}
