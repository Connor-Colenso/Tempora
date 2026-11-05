package com.colen.tempora.loggers.entity_spawn;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntitySpawnEventInfo extends PositionalEventInfo {

    @Column
    public String entityName = "";

    @Column
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
            "tempora.event.entity_spawn",
            EventChat.time(timestamp),
            EventChat.entity(entityName, entityUUID),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_SPAWN;
    }
}
