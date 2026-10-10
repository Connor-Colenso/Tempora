package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;

import com.colen.tempora.TemporaLoggerManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class RenderEventPacket implements IMessage {

    // Null on the client when the event could not be read, see fromBytes.
    public GenericEventInfo eventInfo;

    public RenderEventPacket() {}

    public RenderEventPacket(GenericEventInfo eventInfo) {
        this.eventInfo = eventInfo;
    }

    // Events are sent with their logger's name rather than a numeric id, so the two sides agree even when the server
    // has loggers (from other mods) that the client does not.
    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, eventInfo.getLoggerName());
        eventInfo.toBytes(buf);
    }

    // Any exception thrown while reading a packet disconnects the player, so an event that cannot be read is dropped.
    @Override
    public void fromBytes(ByteBuf buf) {
        String loggerName = ByteBufUtils.readUTF8String(buf);

        // A logger only the server has, e.g. from a server-side mod. Nothing to draw.
        GenericEventInfo event = TemporaLoggerManager.createEventInfo(loggerName);
        if (event == null) return;

        try {
            event.fromBytes(buf);
            eventInfo = event;
        } catch (RuntimeException e) {
            LOG.warn(
                "Could not read a {} event sent by the server, is the mod that adds it a different version on each side?",
                loggerName,
                e);
        }
    }
}
