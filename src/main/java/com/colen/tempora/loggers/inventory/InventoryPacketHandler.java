package com.colen.tempora.loggers.inventory;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class InventoryPacketHandler implements IMessageHandler<InventoryQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final InventoryQueueElement message, MessageContext ctx) {
        Tempora.inventoryLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(InventoryPacketHandler.class, InventoryQueueElement.class, 16, Side.CLIENT);
    }
}
