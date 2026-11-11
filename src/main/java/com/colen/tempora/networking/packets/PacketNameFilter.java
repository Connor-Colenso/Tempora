package com.colen.tempora.networking.packets;

import com.colen.tempora.utils.NameRequests;
import com.colen.tempora.utils.ResolvedNames;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// The client's answer to a PacketResolveNames, what the f: text names in the player's own language. Only IDs
public class PacketNameFilter implements IMessage {

    private int requestID;
    private ResolvedNames names;

    // Needs an empty constructor
    public PacketNameFilter() {}

    public PacketNameFilter(int requestID, ResolvedNames names) {
        this.requestID = requestID;
        this.names = names;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestID = buf.readInt();
        names = ResolvedNames.decode(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestID);
        names.encode(buf);
    }

    public static class Handler implements IMessageHandler<PacketNameFilter, IMessage> {

        @Override
        public IMessage onMessage(PacketNameFilter message, MessageContext ctx) {
            NameRequests.answered(ctx.getServerHandler().playerEntity, message.requestID, message.names);
            return null;
        }
    }
}
