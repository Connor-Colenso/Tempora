package com.colen.tempora.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.colen.tempora.Tempora;
import com.colen.tempora.loggers.block_change.BlockChangeRecordingRegion;

import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Region list sync → client */
public final class PacketShowRegionInWorld {

    @SideOnly(Side.CLIENT)
    public static final List<BlockChangeRecordingRegion> CLIENT_REGIONS = Collections.synchronizedList(new ArrayList<>());

    /** send to one player */
    public static void send(EntityPlayerMP target, List<BlockChangeRecordingRegion> regions) {
        Tempora.NETWORK.sendTo(new RegionMsg(regions), target);
    }

    public static final class RegionMsg implements IMessage {

        private List<BlockChangeRecordingRegion> list = new ArrayList<>();

        public RegionMsg() {}

        public RegionMsg(List<BlockChangeRecordingRegion> list) {
            this.list = list;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(list.size());
            for (BlockChangeRecordingRegion r : list) {
                buf.writeInt(r.dim);
                buf.writeInt(r.minX);
                buf.writeInt(r.minY);
                buf.writeInt(r.minZ);
                buf.writeInt(r.maxX);
                buf.writeInt(r.maxY);
                buf.writeInt(r.maxZ);
            }
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = buf.readInt();
            list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int dim = buf.readInt();
                int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
                int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
                list.add(new BlockChangeRecordingRegion(dim, x1, y1, z1, x2, y2, z2, System.currentTimeMillis()));
            }
        }

        /* client handler */
        public static final class Handler implements IMessageHandler<RegionMsg, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(RegionMsg msg, MessageContext ctx) {
                CLIENT_REGIONS.clear();
                CLIENT_REGIONS.addAll(msg.list);
                return null;
            }
        }
    }

    private PacketShowRegionInWorld() {}
}
