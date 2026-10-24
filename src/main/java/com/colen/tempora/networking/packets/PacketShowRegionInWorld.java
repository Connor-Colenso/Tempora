package com.colen.tempora.networking.packets;

import java.awt.Color;

import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.rendering.ClientRegionStore;
import com.colen.tempora.rendering.regions.RegionRenderMode;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
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

            // Timing. Render start is stamped on arrival, as the server and client clocks can differ.
            buf.writeLong(region.getRegionOriginTimeMs());

            // Metadata
            ByteBufUtils.writeUTF8String(buf, region.getRegionUUID());
            ByteBufUtils.writeUTF8String(buf, region.getPlayerAuthorUUID());
            ByteBufUtils.writeUTF8String(
                buf,
                region.getRenderMode()
                    .name());
            ByteBufUtils.writeUTF8String(buf, region.getLabel());

            // Color
            Color color = region.getColor();
            buf.writeInt(color.getRed());
            buf.writeInt(color.getGreen());
            buf.writeInt(color.getBlue());
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
            long regionOriginTime = buf.readLong();

            // Metadata
            String regionUUID = ByteBufUtils.readUTF8String(buf);
            String playerUUID = ByteBufUtils.readUTF8String(buf);
            RegionRenderMode renderMode = RegionRenderMode.valueOf(ByteBufUtils.readUTF8String(buf));
            String label = ByteBufUtils.readUTF8String(buf);

            // Build region
            region = new TemporaWorldRegion(dim, minX, minY, minZ, maxX, maxY, maxZ);
            region.setRegionOriginTimeMs(regionOriginTime);
            region.setRegionUUID(regionUUID);
            region.setPlayerAuthorUUID(playerUUID);
            region.setRenderMode(renderMode);
            region.setLabel(label);

            // Color
            int red = buf.readInt();
            int green = buf.readInt();
            int blue = buf.readInt();
            region.setColor(new Color(red, green, blue));
        }

        public static final class Handler implements IMessageHandler<RegionMsg, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(RegionMsg msg, MessageContext ctx) {
                msg.region.setRenderStartTimeMs(System.currentTimeMillis());
                ClientRegionStore.add(msg.region);
                return null;
            }
        }
    }
}
