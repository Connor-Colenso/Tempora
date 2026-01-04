package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class EntityDeathPacketHandler implements IMessageHandler<EntityDeathQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final EntityDeathQueueElement message, MessageContext ctx) {
        TemporaLoggerManager.getTypedLogger(TemporaEvents.ENTITY_DEATH).addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(EntityDeathPacketHandler.class, EntityDeathQueueElement.class, 12, Side.CLIENT);
    }
}
