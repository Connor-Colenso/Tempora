package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.utils.GenericUtils.entityUUIDChatComponent;

import com.colen.tempora.enums.LoggerEnum;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;
    public String entityUUID;
    public float rotationYaw;
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
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);
        IChatComponent uuidChatComponent = entityUUIDChatComponent(entityUUID);

        return new ChatComponentTranslation("message.entity_spawn",
            new ChatComponentTranslation("entity." + entityName + ".name"),
            uuidChatComponent, coords, timeAgo);
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntitySpawnLogger;
    }
}
