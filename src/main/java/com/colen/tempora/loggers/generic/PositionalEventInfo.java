package com.colen.tempora.loggers.generic;

import com.colen.tempora.loggers.generic.column.Column;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/** An event that happened at a place, logged by a {@link GenericPositionalLogger}. */
public abstract class PositionalEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public int dimensionID;

    @Column(constraints = "NOT NULL")
    public double x;

    @Column(constraints = "NOT NULL")
    public double y;

    @Column(constraints = "NOT NULL")
    public double z;

    // What the client is sent to draw this event. Subclasses add their own fields after these.
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimensionID = buf.readInt();
        timestamp = buf.readLong();
        eventID = ByteBufUtils.readUTF8String(buf);
        // versionID Not applicable for the client.
    }

    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimensionID);
        buf.writeLong(timestamp);
        ByteBufUtils.writeUTF8String(buf, eventID);
        // versionID Not applicable for the client.
    }
}
