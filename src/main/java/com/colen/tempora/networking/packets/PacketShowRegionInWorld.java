package com.colen.tempora.networking.packets;

import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.rendering.ClientRegionStore;
import com.colen.tempora.rendering.regions.RegionRenderMode;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class PacketShowRegionInWorld {

    public static final class RegionMsg implements IMessage {

        private TemporaWorldRegion region;

        public RegionMsg() {}

        public RegionMsg(TemporaWorldRegion region) {
            this.region = region;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            // Dimension
            buf.writeInt(region.getDimID());

            // Bounds
            buf.writeDouble(region.getMinX());
            buf.writeDouble(region.getMinY());
            buf.writeDouble(region.getMinZ());
            buf.writeDouble(region.getMaxX());
            buf.writeDouble(region.getMaxY());
            buf.writeDouble(region.getMaxZ());

            // Timing
            buf.writeLong(System.currentTimeMillis()); // render start
            buf.writeLong(region.getRegionOriginTimeMs());

            // Metadata
            ByteBufUtils.writeUTF8String(buf, region.getRegionUUID());
            ByteBufUtils.writeUTF8String(buf, region.getPlayerAuthorUUID());
            ByteBufUtils.writeUTF8String(
                buf,
                region.getRenderMode()
                    .name());
            ByteBufUtils.writeUTF8String(buf, region.getLabel());
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            // Dimension
            int dim = buf.readInt();

            // Bounds
            double minX = buf.readDouble();
            double minY = buf.readDouble();
            double minZ = buf.readDouble();
            double maxX = buf.readDouble();
            double maxY = buf.readDouble();
            double maxZ = buf.readDouble();

            // Timing
            long renderStartTime = buf.readLong();
            long regionOriginTime = buf.readLong();

            // Metadata
            String regionUUID = ByteBufUtils.readUTF8String(buf);
            String playerUUID = ByteBufUtils.readUTF8String(buf);
            RegionRenderMode renderMode = RegionRenderMode.valueOf(ByteBufUtils.readUTF8String(buf));
            String label = ByteBufUtils.readUTF8String(buf);

            // Build region
            region = new TemporaWorldRegion(dim, minX, minY, minZ, maxX, maxY, maxZ);
            region.setRenderStartTimeMs(renderStartTime);
            region.setRegionOriginTimeMs(regionOriginTime);
            region.setRegionUUID(regionUUID);
            region.setPlayerAuthorUUID(playerUUID);
            region.setRenderMode(renderMode);
            region.setLabel(label);
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
