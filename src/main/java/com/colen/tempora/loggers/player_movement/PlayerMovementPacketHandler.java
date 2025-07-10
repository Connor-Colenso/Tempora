package com.colen.tempora.loggers.player_movement;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class PlayerMovementPacketHandler implements IMessageHandler<PlayerMovementQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final PlayerMovementQueueElement message, MessageContext ctx) {
        Tempora.playerMovementLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(PlayerMovementPacketHandler.class, PlayerMovementQueueElement.class, 20, Side.CLIENT);
    }
}
