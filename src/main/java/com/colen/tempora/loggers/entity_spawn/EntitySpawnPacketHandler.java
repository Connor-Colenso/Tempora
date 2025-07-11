package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class EntitySpawnPacketHandler implements IMessageHandler<EntitySpawnQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final EntitySpawnQueueElement message, MessageContext ctx) {
        Tempora.entitySpawnLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(EntitySpawnPacketHandler.class, EntitySpawnQueueElement.class, 14, Side.CLIENT);
    }
}
