package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.entityUUIDChatComponent;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityDeathQueueElement extends GenericQueueElement {

    @Column(constraints = "NOT NULL")
    public String nameOfDeadEntity;

    @Column(constraints = "NOT NULL")
    public String killedBy;

    @Column(constraints = "NOT NULL")
    public String entityUUID;

    @Column(constraints = "NOT NULL")
    public float rotationYaw;

    @Column(constraints = "NOT NULL")
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        nameOfDeadEntity = ByteBufUtils.readUTF8String(buf);
        killedBy = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
        rotationYaw = buf.readFloat();
        rotationPitch = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, nameOfDeadEntity);
        ByteBufUtils.writeUTF8String(buf, killedBy);
        ByteBufUtils.writeUTF8String(buf, entityUUID);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationPitch);
    }

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID, CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        IChatComponent uuidChatComponent = entityUUIDChatComponent(entityUUID);

        return new ChatComponentTranslation(
            "message.entity_death",
            new ChatComponentTranslation("entity." + nameOfDeadEntity + ".name"),
            uuidChatComponent,
            PlayerUtils.playerNameFromUUID(killedBy), // Maybe a UUID
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_DEATH;
    }
}
