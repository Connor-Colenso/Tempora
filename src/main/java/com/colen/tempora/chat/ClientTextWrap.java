package com.colen.tempora.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/** Client only: kept apart so a dedicated server never loads the client classes it uses. */
final class ClientTextWrap {

    // Narrower than this and a line holds only a few words, on a small window or a large GUI scale.
    private static final int MIN_WIDTH_PIXELS = 150;

    private ClientTextWrap() {}

    /**
     * Breaks text into lines no wider than three quarters of the screen, in GUI-scaled pixels as hovers are drawn,
     * joined by newlines, which hovers show as lines. Measured each time, so it follows the window size and GUI scale.
     */
    static String wrapToHoverWidth(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        if (font == null) return text;

        int screenWidth = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledWidth();
        int width = Math.max(MIN_WIDTH_PIXELS, screenWidth * 3 / 4);
        return String.join("\n", font.listFormattedStringToWidth(text, width));
    }
}
