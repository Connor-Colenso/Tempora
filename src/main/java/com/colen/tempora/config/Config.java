package com.colen.tempora.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.utils.GenericUtils;

/**
 * Tempora's config files all live in config/tempora/:
 * <ul>
 * <li>tempora.cfg: whether Tempora runs at all ({@link #enabled}).</li>
 * <li>positional_loggers.cfg and global_loggers.cfg: one section per logger of that kind. Read by each logger, with
 * Forge's Configuration, as loggers from other mods add their own sections at runtime.</li>
 * <li>commands.cfg: {@link CommandsConfig}.</li>
 * <li>client.cfg: {@link ClientConfig} and {@link DebugConfig}, client only.</li>
 * </ul>
 */
public class Config {

    // Under the Forge config folder.
    public static final String DIRECTORY = "tempora";

    private static final String GENERAL_CATEGORY = "general";

    // Off by default so Tempora can ship in a pack and do nothing until a server or player turns it on. On by default
    // in the development workspace, where a config written by an earlier run can still turn it off.
    public static boolean enabled;

    private static final String POSITIONAL_LOGGERS_FILE = "positional_loggers.cfg";
    private static final String GLOBAL_LOGGERS_FILE = "global_loggers.cfg";

    private static Configuration positionalLoggersConfig;
    private static Configuration globalLoggersConfig;

    public static void load(File forgeConfigDirectory) {
        File directory = new File(forgeConfigDirectory, DIRECTORY);
        loadEnabled(new Configuration(new File(directory, "tempora.cfg")));
        positionalLoggersConfig = new Configuration(new File(directory, POSITIONAL_LOGGERS_FILE));
        globalLoggersConfig = new Configuration(new File(directory, GLOBAL_LOGGERS_FILE));
    }

    private static void loadEnabled(Configuration configuration) {
        enabled = configuration.getBoolean(
            "enabled",
            GENERAL_CATEGORY,
            GenericUtils.isDevEnvironment(),
            "Turns Tempora on, in singleplayer and on servers alike. While off, nothing is logged, no databases are created and /tempora only says how to turn it on. Each logger can also be turned off on its own, in positional_loggers.cfg or global_loggers.cfg. Needs a restart.");
        // -Dtempora.enabled=true/false wins over the file, for test runs in a real pack (see addon.gradle).
        String enabledOverride = System.getProperty("tempora.enabled");
        if (enabledOverride != null) enabled = Boolean.parseBoolean(enabledOverride);

        if (configuration.hasChanged()) configuration.save();
    }

    /**
     * The file a logger's section is in: each kind of logger has its own, so e.g. logByDefault is only where it
     * applies.
     */
    public static Configuration loggerConfig(GenericLogger<?> logger) {
        return logger instanceof GenericGlobalLogger ? globalLoggersConfig : positionalLoggersConfig;
    }

    /** The name of {@link #loggerConfig}'s file, under config/tempora/. */
    public static String loggerConfigFileName(GenericLogger<?> logger) {
        return logger instanceof GenericGlobalLogger ? GLOBAL_LOGGERS_FILE : POSITIONAL_LOGGERS_FILE;
    }

    /** Saves both logger files if anything in them changed, e.g. a new logger's defaults. */
    public static void saveLoggerConfigs() {
        if (positionalLoggersConfig.hasChanged()) positionalLoggersConfig.save();
        if (globalLoggersConfig.hasChanged()) globalLoggersConfig.save();
    }
}
