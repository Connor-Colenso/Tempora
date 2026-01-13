package com.colen.tempora.networking;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.rendering.ClientRegionStore;
import com.colen.tempora.rendering.regions.RegionRenderMode;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class PacketShowRegionInWorld {

    public static final class RegionMsg implements IMessage {

        private RegionToRender region;

        public RegionMsg() {}

        public RegionMsg(RegionToRender region) {
            this.region = region;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(region.getDimID());
            buf.writeDouble(region.getMinX());
            buf.writeDouble(region.getMinY());
            buf.writeDouble(region.getMinZ());
            buf.writeDouble(region.getMaxX());
            buf.writeDouble(region.getMaxY());
            buf.writeDouble(region.getMaxZ());

            buf.writeLong(System.currentTimeMillis());
            buf.writeLong(region.getRegionOriginTimeMs());

            ByteBufUtils.writeUTF8String(buf, region.getRegionUUID());
            ByteBufUtils.writeUTF8String(buf, region.getPlayerAuthorUUID());
            ByteBufUtils.writeUTF8String(
                buf,
                region.getRenderMode()
                    .toString());
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int dim = buf.readInt();
            double x1 = buf.readDouble(), y1 = buf.readDouble(), z1 = buf.readDouble();
            double x2 = buf.readDouble(), y2 = buf.readDouble(), z2 = buf.readDouble();

            long renderStartTime = buf.readLong();
            long regionOriginTime = buf.readLong();

            String uuid = ByteBufUtils.readUTF8String(buf);
            String playerUUID = ByteBufUtils.readUTF8String(buf);
            RegionRenderMode mode = RegionRenderMode.valueOf(ByteBufUtils.readUTF8String(buf));

            region = new RegionToRender(dim, x1, y1, z1, x2, y2, z2);
            region.setRenderStartTimeMs(renderStartTime);
            region.setRegionOriginTimeMs(regionOriginTime);
            region.setRegionUUID(uuid);
            region.setPlayerAuthorUUID(playerUUID);
            region.setRenderMode(mode);
        }

        public static final class Handler implements IMessageHandler<RegionMsg, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(RegionMsg msg, MessageContext ctx) {
                ClientRegionStore.add(msg.region);
                return null;
            }
        }
    }
}
