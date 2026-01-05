package com.colen.tempora.loggers.generic;

import com.colen.tempora.TemporaLoggerManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import static com.colen.tempora.Tempora.NETWORK;

public class RenderEventPacket implements IMessage {

    public GenericQueueElement queueElement;

    public RenderEventPacket() {}

    public RenderEventPacket(GenericQueueElement queueElement) {
        this.queueElement = queueElement;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(TemporaLoggerManager.getQueueElementId(queueElement));
        queueElement.toBytes(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        byte id = buf.readByte();
        queueElement = TemporaLoggerManager.createQueueElement(id);
        queueElement.fromBytes(buf);
    }

    public void sendEventToClientForRendering(EntityPlayerMP player) {
        NETWORK.sendTo(this, player);
    }

}
