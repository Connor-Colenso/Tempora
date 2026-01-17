package com.colen.tempora.loggers.generic;

import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.column.Column;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public abstract class GenericEventInfo {

    @Column(constraints = "PRIMARY KEY")
    public String eventID;

    @Column(constraints = "NOT NULL")
    public double x;

    @Column(constraints = "NOT NULL")
    public double y;

    @Column(constraints = "NOT NULL")
    public double z;

    @Column(constraints = "NOT NULL")
    public int dimensionID;

    @Column(constraints = "NOT NULL")
    public long timestamp;

    @Column(constraints = "NOT NULL")
    public int versionID;

    // These fields purely dictate rendering info and are not relevant elsewhere.
    public long eventRenderCreationTime;

    public abstract IChatComponent localiseText(String commandIssuerUUID);

    public void populateDefaultFieldsFromResultSet(ResultSet resultSet) throws SQLException {
        x = resultSet.getDouble("x");
        y = resultSet.getDouble("y");
        z = resultSet.getDouble("z");
        dimensionID = resultSet.getInt("dimensionID");
        timestamp = resultSet.getLong("timestamp");
        eventID = resultSet.getString("eventID");
        versionID = resultSet.getInt("versionID");
    }

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

    public boolean needsTransparencyToRender() {
        return false;
    }

    public abstract String getLoggerName();

}
