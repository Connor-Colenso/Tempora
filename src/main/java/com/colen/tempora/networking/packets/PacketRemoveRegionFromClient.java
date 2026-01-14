package com.colen.tempora.networking.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketRemoveRegionFromClient implements IMessage {

    private String uuid;

    // Required empty constructor
    public PacketRemoveRegionFromClient() {}

    public PacketRemoveRegionFromClient(String uuid) {
        this.uuid = uuid;
    }

    public String getUUID() {
        return uuid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.uuid = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.uuid);
    }
}
