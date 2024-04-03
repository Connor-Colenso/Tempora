package com.myname.mymodid;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String debugCategory = "Debug";

    // This is more of a debug option, but can be used in single player if you really want.
    public static boolean shouldTemporaRunInSinglePlayer = false;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        shouldTemporaRunInSinglePlayer = configuration.getBoolean("allowTemporaInSinglePlayer", debugCategory, false, "This is more of a debug option for developers to test easily. Not recommended for players as it adds unnecessary overhead to your world.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
