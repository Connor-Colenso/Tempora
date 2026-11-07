package com.colen.tempora.networking.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

// Sent by the client when a player clicks coords in our chat output to ask to be teleported there. Server checks they
// can use /tempora first since any client can send it
public class PacketTeleportRequest implements IMessage {

    private double x;
    private double y;
    private double z;
    private int dimID;

    // Needs an empty constructor
    public PacketTeleportRequest() {}

    public PacketTeleportRequest(double x, double y, double z, int dimID) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimID = dimID;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getDimID() {
        return dimID;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimID = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimID);
    }
}
