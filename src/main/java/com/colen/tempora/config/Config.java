package com.colen.tempora.config;

import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.utils.TimeUtils;

public class Config {

    private static final String DEBUG_CATEGORY = "Debug";
    public static final String formatCategory = "Format";

    // This is more of a debug option, but can be used in single player if you really want.
    public static boolean shouldTemporaRun;

    public static void synchronizeConfiguration(Configuration configuration) {

        shouldTemporaRun = configuration.getBoolean(
            "shouldTemporaRun",
            DEBUG_CATEGORY,
            false,
            "Runs all logging, not recommended unless you run a public server.");

        TimeUtils.handleConfig(configuration);
    }
}
