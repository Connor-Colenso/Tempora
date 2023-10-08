package com.myname.mymodid.Network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class TempName implements IMessage {

    private double x, y, z;

    // A default constructor is necessary for forge's network code to reconstruct the packet on reception.
    public TempName() {}

    public TempName(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // Decode the data in the order it was added.
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // Encode data in a consistent order.
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
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
}
