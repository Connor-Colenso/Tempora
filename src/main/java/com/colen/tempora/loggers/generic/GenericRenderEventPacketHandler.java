package com.colen.tempora.loggers.generic;

import com.colen.tempora.TemporaLoggerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class GenericRenderEventPacketHandler implements IMessageHandler<RenderEventPacket, IMessage> {

    @Override
    public IMessage onMessage(RenderEventPacket pkt, MessageContext ctx) {
        // Null when this client has no such logger, or could not read the event.
        if (ctx.side == Side.CLIENT && pkt.eventInfo != null) {
            GenericEventInfo event = pkt.eventInfo;
            TemporaLoggerManager.getTypedLogger(event.getLoggerName())
                .addEventToRender(event);
        }
        return null;
    }
}
