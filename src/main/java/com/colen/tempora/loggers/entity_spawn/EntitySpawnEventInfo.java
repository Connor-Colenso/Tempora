package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.GenericUtils.entityUUIDChatComponent;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntitySpawnEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public String entityName;

    @Column(constraints = "NOT NULL")
    public String entityUUID;

    @Column(constraints = "NOT NULL")
    public float rotationYaw;

    @Column(constraints = "NOT NULL")
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        entityName = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, entityName);
        ByteBufUtils.writeUTF8String(buf, entityUUID);
    }

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);
        IChatComponent uuidChatComponent = entityUUIDChatComponent(entityUUID);

        return new ChatComponentTranslation(
            "message.entity_spawn",
            new ChatComponentTranslation("entity." + entityName + ".name"), // todo review
            uuidChatComponent,
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_SPAWN;
    }
}
