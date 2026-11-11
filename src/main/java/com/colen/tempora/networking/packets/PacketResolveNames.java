package com.colen.tempora.networking.packets;

import com.colen.tempora.utils.ClientNameIndex;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// Server asking the client what an f: filter's text matches in the player's language. The client answers with a
// PacketNameFilter, see NameRequests
public class PacketResolveNames implements IMessage {

    private int requestID;
    private String text;

    // Needs an empty constructor
    public PacketResolveNames() {}

    public PacketResolveNames(int requestID, String text) {
        this.requestID = requestID;
        this.text = text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestID = buf.readInt();
        text = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestID);
        ByteBufUtils.writeUTF8String(buf, text);
    }

    // Queued for the client thread since working out names touches items and translations
    public static class Handler implements IMessageHandler<PacketResolveNames, IMessage> {

        @Override
        public IMessage onMessage(PacketResolveNames message, MessageContext ctx) {
            ClientNameIndex.requested(message.requestID, message.text);
            return null;
        }
    }
}
