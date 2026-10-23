package com.colen.tempora.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Tempora's config files all live in config/tempora/:
 * <ul>
 * <li>loggers.cfg: one section per logger, plus whether Tempora runs at all. Read here and by each logger, with
 * Forge's Configuration, as loggers from other mods add their own sections at runtime.</li>
 * <li>commands.cfg: {@link CommandsConfig}.</li>
 * <li>client.cfg: {@link ClientConfig} and {@link DebugConfig}, client only.</li>
 * </ul>
 */
public class Config {

    // Under the Forge config folder.
    public static final String DIRECTORY = "tempora";

    private static final String GENERAL_CATEGORY = "general";
    private static final String DEBUG_CATEGORY = "debug";

    // This is more of a debug option but can be used in a singleplayer world if you really want.
    public static boolean shouldTemporaRun;
    public static boolean randomisedRegionGeneratorBlockChangeDebugger;

    public static Configuration createLoggersConfig(File forgeConfigDirectory) {
        return new Configuration(new File(new File(forgeConfigDirectory, DIRECTORY), "loggers.cfg"));
    }

    public static void synchronizeConfiguration(Configuration configuration) {

        shouldTemporaRun = configuration.getBoolean(
            "shouldTemporaRun",
            GENERAL_CATEGORY,
            false,
            "Runs all logging in singleplayer too. Dedicated servers always log. Not recommended unless you run a public server.");

        randomisedRegionGeneratorBlockChangeDebugger = configuration.getBoolean(
            "randomisedRegionGeneratorBlockChangeDebugger",
            DEBUG_CATEGORY,
            false,
            "WARNING: Every tick creates a randomised block blob at 10,10,10 -> 20,20,20. Never use this in a world you care about.");

        if (configuration.hasChanged()) configuration.save();

    }
}
