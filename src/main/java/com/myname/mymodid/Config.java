package com.myname.mymodid;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String debugCategory = "Debug";
    public static final String loggingIntervals = "Logging Intervals";

    // This is more of a debug option, but can be used in single player if you really want.
    public static boolean shouldTemporaRun;

    public static void synchronizeConfiguration(Configuration configuration) {

        shouldTemporaRun = configuration.getBoolean(
            "shouldTemporaRun",
            debugCategory,
            false,
            "Runs all logging, not recommended unless you run a public server and want grief protection.");

    }
}
