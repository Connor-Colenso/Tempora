package com.colen.tempora.loggers.generic;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.column.Column;

/**
 * What every event shares, whatever logger it belongs to. Events extend one of its two kinds:
 * {@link PositionalEventInfo} for events that happen at a place, or {@link GlobalEventInfo} for events that belong to
 * the whole server.
 */
public abstract class GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public long timestamp;

    @Column(constraints = "PRIMARY KEY")
    public String eventID;

    @Column(constraints = "NOT NULL")
    public int versionID;

    // Only the two kinds extend this directly.
    GenericEventInfo() {}

    public abstract IChatComponent localiseText();

    public abstract String getLoggerName();

}
