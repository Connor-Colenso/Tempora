package com.myname.mymodid.Network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class TempName implements IMessage {

    private double x, y, z;
    private long time;
    private boolean firstPacket;

    // A default constructor is necessary for forge's network code to reconstruct the packet on reception.
    @SuppressWarnings("unused")
    public TempName() {}

    public TempName(double x, double y, double z, long time, boolean firstPacket) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;
        this.firstPacket = firstPacket;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // Decode the data in the order it was added.
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        time = buf.readLong();
        firstPacket = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // Encode data in a consistent order.
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeLong(time);
        buf.writeBoolean(firstPacket);
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

    public long getTime() {
        return time;
    }

    public boolean firstPacket() {
        return firstPacket;
    }
}