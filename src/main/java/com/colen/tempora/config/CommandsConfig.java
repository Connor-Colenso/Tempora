package com.colen.tempora.config;

import com.colen.tempora.Tempora;
import com.gtnewhorizon.gtnhlib.config.Config;

// config/tempora/commands.cfg
@Config(
    modid = Tempora.MODID,
    category = "lookups",
    configSubDirectory = com.colen.tempora.config.Config.DIRECTORY,
    filename = "commands")
public class CommandsConfig {

    @Config.Comment({
        "Longest a lookup (/tempora lookup, global, home, sql or the wand) may run before it is stopped. Lookups run on their own thread, so a slow one never holds up the server, but it does keep everyone else's lookups waiting." })
    @Config.DefaultInt(10)
    @Config.RangeInt(min = 1, max = 600)
    public static int queryTimeLimitSeconds;

    @Config.Comment({
        "Most events /tempora sql shows from one query. Each is a chat line and is drawn in the world, so a high limit floods chat." })
    @Config.DefaultInt(100)
    @Config.RangeInt(min = 1, max = 1000)
    public static int sqlMaxResults;
}
