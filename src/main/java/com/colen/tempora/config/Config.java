package com.colen.tempora.config;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String DEBUG_CATEGORY = "Debug";
    public static final String formatCategory = "Format";

    // This is more of a debug option but can be used in a singleplayer world if you really want.
    public static boolean shouldTemporaRun;
    public static boolean shouldTemporaAlwaysWait;

    public static void synchronizeConfiguration(Configuration configuration) {

        shouldTemporaRun = configuration.getBoolean(
            "shouldTemporaRun",
            DEBUG_CATEGORY,
            false,
            "Runs all logging, not recommended unless you run a public server.");

        shouldTemporaAlwaysWait = configuration.getBoolean(
            "shouldTemporaAlwaysWait",
            DEBUG_CATEGORY,
            false,
            "Prevents the server shutting down if logging has not finished, by default we wait 10 seconds and then shut down.");

    }
}
