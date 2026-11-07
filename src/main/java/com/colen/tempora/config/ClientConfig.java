package com.colen.tempora.config;

import com.colen.tempora.Tempora;
import com.gtnewhorizon.gtnhlib.config.Config;

// config/tempora/client.cfg, shared with DebugConfig
@Config(
    modid = Tempora.MODID,
    category = "rendering",
    configSubDirectory = com.colen.tempora.config.Config.DIRECTORY,
    filename = "client")
public class ClientConfig {

    @Config.Comment({
        "This is the maximum range at which events will render for you, be warned, setting this to a high number may cause performance issues when a large number of events are rendered at once." })
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 4, max = 256)
    public static int eventRenderDistance;
}
