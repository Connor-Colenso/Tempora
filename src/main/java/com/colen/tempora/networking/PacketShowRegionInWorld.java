package com.colen.tempora.networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

import static com.colen.tempora.Tempora.LOG;

/** Region list sync -> client */
public final class PacketShowRegionInWorld {

    @SideOnly(Side.CLIENT)
    public static final List<RegionToRender> CLIENT_BLOCK_CHANGE_REGIONS = Collections.synchronizedList(new ArrayList<>());
    public static final String BLOCK_CHANGE_REGION_CHANNEL_ID = "block_change_region";

    @SideOnly(Side.CLIENT)
    public static final List<RegionToRender> CLIENT_TEMPORA_WAND_REGIONS = Collections.synchronizedList(new ArrayList<>());
    public static final String TEMPORA_WAND_REGION_CHANNEL_ID = "tempora_wand_region";


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
                ByteBufUtils.writeUTF8String(buf, r.channel);
            }
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = buf.readInt();
            list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {

                int dim = buf.readInt();
                double x1 = buf.readDouble(), y1 = buf.readDouble(), z1 = buf.readDouble();
                double x2 = buf.readDouble(), y2 = buf.readDouble(), z2 = buf.readDouble();

                RegionToRender region = new RegionToRender(dim, x1, y1, z1, x2, y2, z2, System.currentTimeMillis());
                region.channel = ByteBufUtils.readUTF8String(buf);

                list.add(region);
            }
        }

        /* client handler */
        public static final class Handler implements IMessageHandler<RegionMsg, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(RegionMsg msg, MessageContext ctx) {
                CLIENT_BLOCK_CHANGE_REGIONS.clear();

                for (RegionToRender r : msg.list) {
                    if (r.channel.equals(BLOCK_CHANGE_REGION_CHANNEL_ID)) {
                        CLIENT_BLOCK_CHANGE_REGIONS.add(r);
                    } else if (r.channel.equals(TEMPORA_WAND_REGION_CHANNEL_ID)) {
                        CLIENT_TEMPORA_WAND_REGIONS.add(r);
                    } else {
                        LOG.error("Unknown channel for tempora region renderer: {}", r.channel);
                    }
                }

                return null;
            }
        }
    }

    private PacketShowRegionInWorld() {}
}
