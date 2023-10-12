package com.myname.mymodid.Commands.HeatMap.Network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class HeatMapPacket implements IMessage {

    private int[] x, y, z;
    private double[] intensity;
    private boolean firstPacket, lastPacket;

    // A default constructor is necessary for forge's network code to reconstruct the packet on reception.
    public HeatMapPacket() {}

    public HeatMapPacket(int[] x, int[] y, int[] z, double[] intensity, boolean firstPacket, boolean lastPacket) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.intensity = intensity;

        this.firstPacket = firstPacket;
        this.lastPacket = lastPacket;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int length = buf.readInt();

        x = new int[length];
        y = new int[length];
        z = new int[length];
        intensity = new double[length];

        for (int i = 0; i < length; i++) {
            x[i] = buf.readInt();
            y[i] = buf.readInt();
            z[i] = buf.readInt();
            intensity[i] = buf.readDouble();
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
            buf.writeDouble(intensity[i]);
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

    public double[] getIntensity() {
        return intensity;
    }

    public boolean isFirstPacket() {
        return firstPacket;
    }

    public boolean isLastPacket() {
        return lastPacket;
    }

}
