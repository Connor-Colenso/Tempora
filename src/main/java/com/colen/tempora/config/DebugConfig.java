package com.colen.tempora.config;

import com.colen.tempora.Tempora;
import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = Tempora.MODID, category = "debug_options", filename = "tempora_debug")
public class DebugConfig {

    @Config.Comment({
        "Shows the rendering of ID:META in the center of every non-air block nearby. This is purely a debugging tool.", })
    @Config.DefaultBoolean(false)
    public static boolean showIDMetaRenderInBlocksNearby;

    // Tune this carefully – cube of this cost

    @Config.Comment({ "Radius in blocks of the ID:META rendering. Setting this high will lag." })
    @Config.DefaultInt(6)
    @Config.RangeInt(min = 2, max = 100)
    public static int IDMetaRenderingRadius;

}
