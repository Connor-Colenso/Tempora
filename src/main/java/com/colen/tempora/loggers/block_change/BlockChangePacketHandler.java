package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class BlockChangePacketHandler implements IMessageHandler<BlockChangeQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final BlockChangeQueueElement message, MessageContext ctx) {
        Tempora.blockChangeLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(BlockChangePacketHandler.class, BlockChangeQueueElement.class, 10, Side.CLIENT);
    }

}
