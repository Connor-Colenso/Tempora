package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;

import net.minecraft.entity.player.EntityPlayerMP;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.networking.PlayerPackets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class RenderEventPacket implements IMessage {

    // Null on the client when the event could not be read, see fromBytes.
    public PositionalEventInfo eventInfo;

    public RenderEventPacket() {}

    public RenderEventPacket(PositionalEventInfo eventInfo) {
        this.eventInfo = eventInfo;
    }

    /**
     * Sends a player an event to draw, unless writing it throws, e.g. a bug in another mod's event, which is logged
     * instead. It is written once first, so a broken event is caught here rather than part way through sending.
     */
    public static void sendSafely(PositionalEventInfo event, EntityPlayerMP player) {
        RenderEventPacket packet = new RenderEventPacket(event);
        try {
            packet.toBytes(Unpooled.buffer());
        } catch (RuntimeException e) {
            LOG.error("Could not send {} event {} to draw, so it is skipped.", event.getLoggerName(), event.eventID, e);
            return;
        }
        PlayerPackets.sendTo(packet, player);
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

        // A logger only the server has, e.g. from a server-side mod. Nothing to draw. Only positional events are sent,
        // so one that is not positional here means the two sides have different versions of its mod.
        if (!(TemporaLoggerManager.createEventInfo(loggerName) instanceof PositionalEventInfo event)) return;

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
