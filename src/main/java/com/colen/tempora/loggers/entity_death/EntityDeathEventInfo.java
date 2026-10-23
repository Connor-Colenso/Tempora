package com.colen.tempora.loggers.entity_death;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityDeathEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL")
    public String nameOfDeadEntity = "";

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
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent victim = EventChat.entity(nameOfDeadEntity, entityUUID);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.EXACT);

        // Players are stored by UUID. Anything else is in brackets: a mob by its EntityList name, e.g. [Zombie] (older
        // rows have its class, e.g. [EntityZombie]), or a damage type such as [fall] or [inFire].
        if (PlayerUtils.isUUID(killedBy)) {
            return EventChat.line("message.entity_death.killed", when, EventChat.player(killedBy), victim, where);
        }

        if (killedBy != null && killedBy.length() > 2 && killedBy.startsWith("[") && killedBy.endsWith("]")) {
            String cause = killedBy.substring(1, killedBy.length() - 1);
            if (isEntity(cause)) {
                return EventChat.line("message.entity_death.killed", when, EventChat.entityName(cause), victim, where);
            }

            // inFire -> in fire.
            String damage = cause.replaceAll("([a-z])([A-Z])", "$1 $2")
                .toLowerCase(Locale.ROOT);
            return EventChat.line(
                "message.entity_death.died",
                when,
                victim,
                EventChat.text(damage, EnumChatFormatting.WHITE),
                where);
        }

        return EventChat.line("message.entity_death.unknown", when, victim, where);
    }

    // Vanilla's damage types start lower case and have no translated entity name, mobs do one or the other.
    private static boolean isEntity(String cause) {
        return Character.isUpperCase(cause.charAt(0)) || StatCollector.canTranslate("entity." + cause + ".name");
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_DEATH;
    }
}
