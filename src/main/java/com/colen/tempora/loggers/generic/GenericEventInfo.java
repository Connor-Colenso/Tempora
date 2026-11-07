package com.colen.tempora.loggers.generic;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.column.Column;

/**
 * Shared by every event. Events extend one of the two kinds, {@link PositionalEventInfo} for stuff at a place or
 * {@link GlobalEventInfo} for server wide stuff
 */
public abstract class GenericEventInfo {

    @Column
    public long timestamp;

    // The table's rowid (INTEGER PRIMARY KEY) so it's free to store and index. SQLite assigns it on write counting up,
    // so it's 0 until then and only unique within its own logger
    @Column
    public long eventID;

    @Column
    public int versionID;

    // Only the two kinds extend this directly
    GenericEventInfo() {}

    public abstract IChatComponent localiseText();

    public abstract String getLoggerName();

}
