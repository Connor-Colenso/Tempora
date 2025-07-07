package com.colen.tempora.networking;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PacketDetectedInfo {

    // client cache
    @SideOnly(Side.CLIENT)
    public static final List<Pos> CLIENT_POS =
        Collections.synchronizedList(new ArrayList<>());

    // send to one player
    public static void send(EntityPlayerMP target, List<Pos> list) {
        Tempora.NETWORK.sendTo(new PosMessage(list), target);
    }

    // single record
    public static final class Pos {
        public final double x, y, z;
        public final int dim;
        public final long posPrintTime;

        public Pos(double x, double y, double z, int dim, long posPrintTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.posPrintTime = posPrintTime;
        }

        public void write(ByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeInt(dim);
            buf.writeLong(posPrintTime);
        }

        public static Pos read(ByteBuf buf) {
            return new Pos(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readLong());
        }
    }

    // message with list
    public static final class PosMessage implements IMessage {
        private List<Pos> list = new ArrayList<>();

        public PosMessage() {}
        public PosMessage(List<Pos> list) { this.list = list; }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(list.size());
            for (Pos p : list) p.write(buf);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = buf.readInt();
            list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(Pos.read(buf));
        }

        // client handler
        public static final class Handler implements IMessageHandler<PosMessage, IMessage> {
            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(PosMessage msg, MessageContext ctx) {
                CLIENT_POS.addAll(msg.list);
                return null;
            }
        }
    }

    private PacketDetectedInfo() {}
}
