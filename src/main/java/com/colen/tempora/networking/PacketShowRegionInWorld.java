package com.colen.tempora.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Region list sync -> client */
public final class PacketShowRegionInWorld {

    @SideOnly(Side.CLIENT)
    public static final List<RegionToRender> CLIENT_REGIONS = Collections.synchronizedList(new ArrayList<>());

    public static final class RegionMsg implements IMessage {

        private List<RegionToRender> list = new ArrayList<>();

        // Empty constructor required to instantiate from reflection later.
        @SuppressWarnings("unused")
        public RegionMsg() {}

        public RegionMsg(List<RegionToRender> list) {
            this.list = list;
        }

        public RegionMsg(RegionToRender region) {
            this.list = new ArrayList<>();
            this.list.add(region);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(list.size());
            for (RegionToRender r : list) {
                buf.writeInt(r.dim);
                buf.writeDouble(r.minX);
                buf.writeDouble(r.minY);
                buf.writeDouble(r.minZ);
                buf.writeDouble(r.maxX);
                buf.writeDouble(r.maxY);
                buf.writeDouble(r.maxZ);
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
                list.add(new RegionToRender(dim, x1, y1, z1, x2, y2, z2, System.currentTimeMillis()));
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
