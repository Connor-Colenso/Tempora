package com.colen.tempora.loggers.generic;

import com.colen.tempora.loggers.generic.column.Column;

import io.netty.buffer.ByteBuf;

/** An event that happened at a place, logged by a {@link GenericPositionalLogger}. */
public abstract class PositionalEventInfo extends GenericEventInfo {

    @Column
    public int dimensionID;

    @Column
    public double x;

    @Column
    public double y;

    @Column
    public double z;

    // What the client is sent to draw this event. Subclasses add their own fields after these.
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimensionID = buf.readInt();
        timestamp = buf.readLong();
        eventID = buf.readLong();
        // versionID Not applicable for the client.
    }

    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimensionID);
        buf.writeLong(timestamp);
        buf.writeLong(eventID);
        // versionID Not applicable for the client.
    }
}
