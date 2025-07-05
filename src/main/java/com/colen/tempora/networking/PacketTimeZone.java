package com.colen.tempora.networking;

import net.minecraft.entity.player.EntityPlayerMP;

import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketTimeZone implements IMessage {

    private String timezoneId;

    public PacketTimeZone() {}

    public PacketTimeZone(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, timezoneId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        timezoneId = ByteBufUtils.readUTF8String(buf);
    }

    // Get the timezone and add it to our serverside map.
    public static class Handler implements IMessageHandler<PacketTimeZone, IMessage> {

        @Override
        public IMessage onMessage(PacketTimeZone message, MessageContext ctx) {
            EntityPlayerMP serverPlayer = ctx.getServerHandler().playerEntity;
            TimeUtils.setTimeZone(
                serverPlayer.getUniqueID()
                    .toString(),
                message.timezoneId);
            return null;
        }
    }
}
