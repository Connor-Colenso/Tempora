package com.colen.tempora.networking.packets;

import com.colen.tempora.utils.ServerVersionCheck;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// Server's Tempora version, sent on login to players who can use /tempora so their client can say if it's different.
// Never change this layout, every version has to read it
public class PacketServerVersion implements IMessage {

    private String version;

    // Needs an empty constructor
    public PacketServerVersion() {}

    public PacketServerVersion(String version) {
        this.version = version;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        version = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, version);
    }

    // Arrives before the client has a player to show chat to, so it waits for the client thread
    public static class Handler implements IMessageHandler<PacketServerVersion, IMessage> {

        @Override
        public IMessage onMessage(PacketServerVersion message, MessageContext ctx) {
            ServerVersionCheck.received(message.version);
            return null;
        }
    }
}
