package com.colen.tempora.loggers.player_movement;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class PlayerMovementPacketHandler implements IMessageHandler<PlayerMovementQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final PlayerMovementQueueElement message, MessageContext ctx) {
        TemporaLoggerManager.getTypedLogger(TemporaEvents.PLAYER_MOVEMENT).addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(PlayerMovementPacketHandler.class, PlayerMovementQueueElement.class, 20, Side.CLIENT);
    }
}
