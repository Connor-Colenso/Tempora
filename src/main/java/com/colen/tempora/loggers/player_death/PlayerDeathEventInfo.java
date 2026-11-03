package com.colen.tempora.loggers.player_death;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.entity_death.DeathCause;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerDeathEventInfo extends PositionalEventInfo {

    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    // See DeathCause.
    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String killerPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public String killedBy = PlayerUtils.UNKNOWN_CAUSE;

    // The XP level they had, which dying loses unless keepInventory is on.
    @Column(constraints = "NOT NULL")
    public int xpLevel;

    // The keepInventory game rule, which decides whether they dropped everything.
    @Column(constraints = "NOT NULL")
    public boolean keepInventory;

    @Column(constraints = "NOT NULL")
    public float rotationYaw;

    @Column(constraints = "NOT NULL")
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
        killerPlayerUUID = ByteBufUtils.readUTF8String(buf);
        killedBy = ByteBufUtils.readUTF8String(buf);
        xpLevel = buf.readInt();
        keepInventory = buf.readBoolean();
        rotationYaw = buf.readFloat();
        rotationPitch = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        ByteBufUtils.writeUTF8String(buf, killerPlayerUUID);
        ByteBufUtils.writeUTF8String(buf, killedBy);
        buf.writeInt(xpLevel);
        buf.writeBoolean(keepInventory);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationPitch);
    }

    @Override
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent victim = EventChat.player(playerUUID);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.EXACT);
        IChatComponent level = new ChatComponentTranslation(
            keepInventory ? "tempora.event.player_death.level_kept" : "tempora.event.player_death.level",
            EventChat.text(formatNumber(xpLevel), EnumChatFormatting.WHITE));

        IChatComponent killer = DeathCause.killer(killerPlayerUUID, killedBy);
        if (killer != null) {
            return EventChat.line("tempora.event.player_death.killed", when, victim, killer, where, level);
        }

        IChatComponent damage = DeathCause.damage(killedBy);
        if (damage != null) {
            return EventChat.line("tempora.event.player_death.died", when, victim, damage, where, level);
        }

        return EventChat.line("tempora.event.player_death.unknown", when, victim, where, level);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_DEATH;
    }
}
