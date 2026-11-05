package com.colen.tempora.loggers.entity_position;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityPositionEventInfo extends PositionalEventInfo {

    // The mob's number in this logger's Mobs table, which holds its UUID and name once however often it is logged.
    // Given by the logger as the event is stored, see EntityPositionLogger.
    @Column
    public long mobID;

    // Not columns: stored in the Mobs table. Set as the mob is logged, and filled in again as events are read back.
    public String entityName = "";
    public String entityUUID = "";

    @Column
    public float rotationYaw;

    @Column
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        entityName = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
        rotationYaw = buf.readFloat();
        rotationPitch = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, entityName);
        ByteBufUtils.writeUTF8String(buf, entityUUID);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationPitch);
    }

    @Override
    public IChatComponent localiseText() {
        return EventChat.line(
            "tempora.event.entity_position",
            EventChat.time(timestamp),
            EventChat.entity(entityName, entityUUID),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_POSITION;
    }
}
