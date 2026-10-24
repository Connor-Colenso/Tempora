package com.colen.tempora.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.utils.GenericUtils;

/**
 * Tempora's config files all live in config/tempora/:
 * <ul>
 * <li>loggers.cfg: one section per logger, plus whether Tempora runs at all ({@link #enabled}). Read here and by each
 * logger, with Forge's Configuration, as loggers from other mods add their own sections at runtime.</li>
 * <li>commands.cfg: {@link CommandsConfig}.</li>
 * <li>client.cfg: {@link ClientConfig} and {@link DebugConfig}, client only.</li>
 * </ul>
 */
public class Config {

    // Under the Forge config folder.
    public static final String DIRECTORY = "tempora";

    private static final String GENERAL_CATEGORY = "general";
    private static final String DEBUG_CATEGORY = "debug";

    // Off by default so Tempora can ship in a pack and do nothing until a server or player turns it on. On by default
    // in the development workspace, where a config written by an earlier run can still turn it off.
    public static boolean enabled;
    public static boolean randomisedRegionGeneratorBlockChangeDebugger;

    public static Configuration createLoggersConfig(File forgeConfigDirectory) {
        return new Configuration(new File(new File(forgeConfigDirectory, DIRECTORY), "loggers.cfg"));
    }

    public static void synchronizeConfiguration(Configuration configuration) {

        enabled = configuration.getBoolean(
            "enabled",
            GENERAL_CATEGORY,
            GenericUtils.isDevEnvironment(),
            "Turns Tempora on, in singleplayer and on servers alike. While off, nothing is logged, no databases are created and /tempora only says how to turn it on. Needs a restart.");
        // -Dtempora.enabled=true/false wins over the file, for test runs in a real pack (see addon.gradle).
        String enabledOverride = System.getProperty("tempora.enabled");
        if (enabledOverride != null) enabled = Boolean.parseBoolean(enabledOverride);

        randomisedRegionGeneratorBlockChangeDebugger = configuration.getBoolean(
            "randomisedRegionGeneratorBlockChangeDebugger",
            DEBUG_CATEGORY,
            false,
            "WARNING: Every tick creates a randomised block blob at 10,10,10 -> 20,20,20. Never use this in a world you care about.");

        if (configuration.hasChanged()) configuration.save();

    }
}
