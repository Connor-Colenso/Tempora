package com.colen.tempora.networking.handlers;

import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.rendering.ClientRegionStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PacketRemoveClientRegionByUUID implements IMessageHandler<PacketRemoveRegionFromClient, IMessage> {

    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(final PacketRemoveRegionFromClient message, MessageContext ctx) {
        handleRemoveRegionByUUID(message.getUUID());
        return null; // one-way packet
    }

    @SideOnly(Side.CLIENT)
    private void handleRemoveRegionByUUID(String uuid) {
        ClientRegionStore.remove(uuid);
    }
}
