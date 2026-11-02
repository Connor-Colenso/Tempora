package com.colen.tempora.loggers.entity_death;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityDeathEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL")
    public String entityName = "";

    // See DeathCause.
    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String killerPlayer = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public String killedBy = PlayerUtils.UNKNOWN_CAUSE;

    @Column(constraints = "NOT NULL")
    public String entityUUID = "";

    @Column(constraints = "NOT NULL")
    public float rotationYaw;

    @Column(constraints = "NOT NULL")
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        entityName = ByteBufUtils.readUTF8String(buf);
        killerPlayer = ByteBufUtils.readUTF8String(buf);
        killedBy = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
        rotationYaw = buf.readFloat();
        rotationPitch = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, entityName);
        ByteBufUtils.writeUTF8String(buf, killerPlayer);
        ByteBufUtils.writeUTF8String(buf, killedBy);
        ByteBufUtils.writeUTF8String(buf, entityUUID);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationPitch);
    }

    @Override
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent victim = EventChat.entity(entityName, entityUUID);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.EXACT);

        IChatComponent killer = DeathCause.killer(killerPlayer, killedBy);
        if (killer != null) return EventChat.line("tempora.event.entity_death.killed", when, killer, victim, where);

        IChatComponent damage = DeathCause.damage(killedBy);
        if (damage != null) return EventChat.line("tempora.event.entity_death.died", when, victim, damage, where);

        return EventChat.line("tempora.event.entity_death.unknown", when, victim, where);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_DEATH;
    }
}
