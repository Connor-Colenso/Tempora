package com.colen.tempora.loggers.player_block_place;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class PlayerBlockPlacePacketHandler implements IMessageHandler<PlayerBlockPlaceQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final PlayerBlockPlaceQueueElement message, MessageContext ctx) {
        Tempora.playerBlockPlaceLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(PlayerBlockPlacePacketHandler.class, PlayerBlockPlaceQueueElement.class, 19, Side.CLIENT);
    }
}
