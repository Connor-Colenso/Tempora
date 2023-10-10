package com.myname.mymodid.Network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PlayerPositionPacket implements IMessage {

    private int[] x, y, z;
    private long[] time;

    public boolean firstPacket;
    public boolean lastPacket;

    // A default constructor is necessary for forge's network code to reconstruct the packet on reception.
    public PlayerPositionPacket() {}

    public PlayerPositionPacket(int[] x, int[] y, int[] z, long[] time, boolean firstPacket, boolean lastPacket) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;
        this.firstPacket = firstPacket;
        this.lastPacket = lastPacket;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int length = buf.readInt();
        x = new int[length];
        y = new int[length];
        z = new int[length];
        time = new long[length];

        for (int i = 0; i < length; i++) {
            x[i] = buf.readInt();
            y[i] = buf.readInt();
            z[i] = buf.readInt();
            time[i] = buf.readLong();
        }

        firstPacket = buf.readBoolean();
        lastPacket = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x.length);
        for (int i = 0; i < x.length; i++) {
            buf.writeInt(x[i]);
            buf.writeInt(y[i]);
            buf.writeInt(z[i]);
            buf.writeLong(time[i]);
        }

        buf.writeBoolean(firstPacket);
        buf.writeBoolean(lastPacket);
    }

    public int[] getX() {
        return x;
    }

    public int[] getY() {
        return y;
    }

    public int[] getZ() {
        return z;
    }

    public long[] getTime() {
        return time;
    }

}
