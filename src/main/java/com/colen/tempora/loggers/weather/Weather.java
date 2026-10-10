package com.colen.tempora.loggers.weather;

import java.util.Locale;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/** The weather players see in a world. Stored in the database by name. */
public enum Weather {

    CLEAR,
    RAIN,
    THUNDER;

    // What the world shows right now, rather than the target its weather is moving towards. This is what players see,
    // and also catches dimensions whose mods run their own weather.
    public static Weather of(World world) {
        if (world.isThundering()) return THUNDER;
        if (world.isRaining()) return RAIN;
        return CLEAR;
    }

    // A stored name, translated. Shown as stored if it is not one of these, e.g. written by a newer version.
    public static IChatComponent chatComponent(String storedName) {
        for (Weather weather : values()) {
            if (weather.name()
                .equals(storedName)) {
                return new ChatComponentTranslation(
                    "tempora.weather." + weather.name()
                        .toLowerCase(Locale.ROOT));
            }
        }
        return new ChatComponentText(storedName);
    }
}
