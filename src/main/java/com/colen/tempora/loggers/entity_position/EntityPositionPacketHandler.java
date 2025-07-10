package com.colen.tempora.loggers.entity_position;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class EntityPositionPacketHandler implements IMessageHandler<EntityPositionQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final EntityPositionQueueElement message, MessageContext ctx) {
        Tempora.entityPositionLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(EntityPositionPacketHandler.class, EntityPositionQueueElement.class, 13, Side.CLIENT);
    }
}
