package com.colen.tempora.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.utils.GenericUtils;

// All our config files live in config/tempora/
// - tempora.cfg, whether Tempora runs at all (enabled)
// - positional_loggers.cfg and global_loggers.cfg, a section per logger of that kind. Each logger reads its own with
// Forge's Configuration since loggers from other mods add their own sections at runtime
// - commands.cfg, CommandsConfig
// - client.cfg, ClientConfig and DebugConfig, client only
public class Config {

    // Under the Forge config folder
    public static final String DIRECTORY = "tempora";

    private static final String GENERAL_CATEGORY = "general";

    // Off by default so it can ship in GTNH and do nothing until a server or player turns it on. On by default in the
    // dev workspace, where a config from an earlier run can still turn it off
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
        // Headless Horizon-QA runs (pack CI, runFullPackServerTests) turn it on so the gametests test something. The
        // pack ships it off. Never saved to the file
        if ("ci".equalsIgnoreCase(System.getProperty("horizonqa.mode"))) enabled = true;
        // -Dtempora.enabled=true/false beats both, e.g. to check how the pack behaves with it off
        String enabledOverride = System.getProperty("tempora.enabled");
        if (enabledOverride != null) enabled = Boolean.parseBoolean(enabledOverride);

        if (configuration.hasChanged()) configuration.save();
    }

    // File a logger's section lives in, each kind gets its own so e.g. logByDefault only shows where it applies
    public static Configuration loggerConfig(GenericLogger<?> logger) {
        return logger instanceof GenericGlobalLogger ? globalLoggersConfig : positionalLoggersConfig;
    }

    // Name of loggerConfig's file under config/tempora/
    public static String loggerConfigFileName(GenericLogger<?> logger) {
        return logger instanceof GenericGlobalLogger ? GLOBAL_LOGGERS_FILE : POSITIONAL_LOGGERS_FILE;
    }

    // Saves both logger files if anything changed, e.g. a new logger's defaults
    public static void saveLoggerConfigs() {
        if (positionalLoggersConfig.hasChanged()) positionalLoggersConfig.save();
        if (globalLoggersConfig.hasChanged()) globalLoggersConfig.save();
    }
}
