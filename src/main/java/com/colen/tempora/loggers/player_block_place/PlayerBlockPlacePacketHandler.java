package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.Tempora.NETWORK;

import com.colen.tempora.Tempora;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class PlayerBlockPlacePacketHandler implements IMessageHandler<PlayerBlockPlaceQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final PlayerBlockPlaceQueueElement message, MessageContext ctx) {
        TemporaLoggerManager.getTypedLogger(TemporaEvents.PLAYER_BLOCK_PLACE).addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK
            .registerMessage(PlayerBlockPlacePacketHandler.class, PlayerBlockPlaceQueueElement.class, 19, Side.CLIENT);
    }
}
