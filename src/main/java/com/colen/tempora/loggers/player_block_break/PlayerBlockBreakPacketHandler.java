package com.colen.tempora.loggers.player_block_break;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import static com.colen.tempora.Tempora.NETWORK;

public class PlayerBlockBreakPacketHandler implements IMessageHandler<PlayerBlockBreakQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final PlayerBlockBreakQueueElement message, MessageContext ctx) {
        Tempora.playerBlockBreakLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(PlayerBlockBreakPacketHandler.class, PlayerBlockBreakQueueElement.class, 18, Side.CLIENT);
    }
}
