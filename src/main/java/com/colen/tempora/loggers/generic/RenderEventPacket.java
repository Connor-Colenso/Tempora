package com.colen.tempora.loggers.generic;

import com.colen.tempora.TemporaLoggerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class RenderEventPacket implements IMessage {

    public GenericQueueElement queueElement;

    public RenderEventPacket() {}

    public RenderEventPacket(GenericQueueElement queueElement) {
        this.queueElement = queueElement;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(TemporaLoggerManager.getQueueElementId(queueElement));
        queueElement.toBytes(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int id = buf.readInt();
        queueElement = TemporaLoggerManager.createQueueElement(id);
        queueElement.fromBytes(buf);
    }
}
