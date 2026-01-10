package com.colen.tempora.loggers.generic;

import com.colen.tempora.TemporaLoggerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class RenderEventPacket implements IMessage {

    public GenericEventInfo eventInfo;

    public RenderEventPacket() {}

    public RenderEventPacket(GenericEventInfo eventInfo) {
        this.eventInfo = eventInfo;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(TemporaLoggerManager.getEventInfoId(eventInfo));
        eventInfo.toBytes(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int id = buf.readInt();
        eventInfo = TemporaLoggerManager.createEventInfo(id);
        eventInfo.fromBytes(buf);
    }
}
