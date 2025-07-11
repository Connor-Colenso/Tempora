package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.entityUUIDChatComponent;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;
    public String entityUUID;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        nameOfDeadMob = ByteBufUtils.readUTF8String(buf);
        killedBy = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, nameOfDeadMob);
        ByteBufUtils.writeUTF8String(buf, killedBy);
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

        return new ChatComponentTranslation(
            "message.entity_death",
            nameOfDeadMob,
            uuidChatComponent,
            killedBy,
            coords,
            timeAgo);
    }
}
