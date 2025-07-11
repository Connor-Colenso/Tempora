package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class ItemUsePacketHandler implements IMessageHandler<ItemUseQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final ItemUseQueueElement message, MessageContext ctx) {
        Tempora.itemUseLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(ItemUsePacketHandler.class, ItemUseQueueElement.class, 17, Side.CLIENT);
    }
}
