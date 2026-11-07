package com.colen.tempora.loggers.weather;

import java.util.Locale;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

// Weather players see in a world, stored by name
public enum Weather {

    CLEAR(EnumChatFormatting.GOLD),
    RAIN(EnumChatFormatting.BLUE),
    THUNDER(EnumChatFormatting.DARK_PURPLE);

    // Chat colour
    private final EnumChatFormatting colour;

    Weather(EnumChatFormatting colour) {
        this.colour = colour;
    }

    // What the world shows right now, not where its weather is heading. That's what players see, and it catches dims
    // whose mods run their own weather
    public static Weather of(World world) {
        if (world.isThundering()) return THUNDER;
        if (world.isRaining()) return RAIN;
        return CLEAR;
    }

    // Stored name translated, shown as is if it's not one of these (e.g. written by a newer version)
    public static IChatComponent chatComponent(String storedName) {
        for (Weather weather : values()) {
            if (weather.name()
                .equals(storedName)) {
                IChatComponent name = new ChatComponentTranslation(
                    "tempora.weather." + weather.name()
                        .toLowerCase(Locale.ROOT));
                name.getChatStyle()
                    .setColor(weather.colour);
                return name;
            }
        }
        return new ChatComponentText(storedName);
    }
}
