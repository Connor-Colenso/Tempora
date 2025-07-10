package com.colen.tempora.loggers.explosion;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class ExplosionPacketHandler implements IMessageHandler<ExplosionQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final ExplosionQueueElement message, MessageContext ctx) {
        Tempora.explosionLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(ExplosionPacketHandler.class, ExplosionQueueElement.class, 15, Side.CLIENT);
    }
}
