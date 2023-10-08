package com.myname.mymodid.Network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PlayerPositionPacket implements IMessage {

    private double[] x, y, z;
    private long[] time;

    public boolean firstPacket;
    public boolean lastPacket;

    // A default constructor is necessary for forge's network code to reconstruct the packet on reception.
    @SuppressWarnings("unused")
    public PlayerPositionPacket() {}

    public PlayerPositionPacket(double[] x, double[] y, double[] z, long[] time) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int length = buf.readInt();
        x = new double[length];
        y = new double[length];
        z = new double[length];
        time = new long[length];

        for (int i = 0; i < length; i++) {
            x[i] = buf.readDouble();
            y[i] = buf.readDouble();
            z[i] = buf.readDouble();
            time[i] = buf.readLong();
        }

        firstPacket = buf.readBoolean();
        lastPacket = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x.length); // assuming x, y, z, and time all have the same length
        for (int i = 0; i < x.length; i++) {
            buf.writeDouble(x[i]);
            buf.writeDouble(y[i]);
            buf.writeDouble(z[i]);
            buf.writeLong(time[i]);
        }

        buf.writeBoolean(firstPacket);
        buf.writeBoolean(lastPacket);
    }

    public double[] getX() {
        return x;
    }

    public double[] getY() {
        return y;
    }

    public double[] getZ() {
        return z;
    }

    public long[] getTime() {
        return time;
    }

    public boolean isFirstPacket() {
        return firstPacket;
    }

    public boolean isLastPacket() {
        return lastPacket;
    }

}
