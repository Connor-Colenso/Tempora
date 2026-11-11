package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static cpw.mods.fml.common.network.ByteBufUtils.readVarInt;
import static cpw.mods.fml.common.network.ByteBufUtils.varIntByteCount;
import static cpw.mods.fml.common.network.ByteBufUtils.writeVarInt;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.Validate;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.lookups.MobNames;
import com.colen.tempora.utils.ChunkPositionUtils;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.google.common.base.Charsets;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ExplosionEventInfo extends PositionalEventInfo {

    @Column
    public float power;
    @Column(nameRole = MobNames.class)
    public String exploder = PlayerUtils.UNKNOWN_CAUSE;
    @Column(type = ColumnType.PLAYER)
    public String closestPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;
    @Column
    public double closestPlayerDistance;
    @Column
    public String affectedBlockCoordinates = "";

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        power = buf.readFloat();
        exploder = ByteBufUtils.readUTF8String(buf);
        closestPlayerUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerDistance = buf.readDouble();
        affectedBlockCoordinates = readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeFloat(power);
        ByteBufUtils.writeUTF8String(buf, exploder);
        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
        writeUTF8String(buf, affectedBlockCoordinates);
    }

    // Gets round the string length limit for big affectedBlockCoordinates lists. TODO review this
    public static void writeUTF8String(ByteBuf to, String string) {
        byte[] utf8Bytes = string.getBytes(Charsets.UTF_8);
        // Up to 4 bytes for the length VarInt (~268 MB)
        Validate.isTrue(varIntByteCount(utf8Bytes.length) <= 3, "The string is too long for this encoding.");
        writeVarInt(to, utf8Bytes.length, 3);
        to.writeBytes(utf8Bytes);
    }

    public static String readUTF8String(ByteBuf from) {
        int len = readVarInt(from, 3);
        String str = from.toString(from.readerIndex(), len, Charsets.UTF_8);
        from.readerIndex(from.readerIndex() + len);
        return str;
    }

    @Override
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.EXACT);
        IChatComponent powerText = EventChat.text(formatNumber(power, ONE_DP), EnumChatFormatting.WHITE);
        IChatComponent destroyed = EventChat.text(
            formatNumber(
                ChunkPositionUtils.decodePositions(affectedBlockCoordinates)
                    .size()),
            EnumChatFormatting.WHITE);

        // Player by UUID, anything else by EntityList name
        IChatComponent exploderText;
        if (exploder == null || UNKNOWN_CAUSE.equals(exploder)) {
            exploderText = new ChatComponentTranslation(UNKNOWN_CAUSE);
            exploderText.getChatStyle()
                .setColor(EnumChatFormatting.WHITE);
        } else {
            exploderText = PlayerUtils.isUUID(exploder) ? EventChat.player(exploder)
                : EventChat.searchable(EventChat.entityName(exploder));
        }

        // Negative distance means no player was in the world
        if (closestPlayerDistance < 0) {
            return EventChat.line("tempora.event.explosion.no_player", when, exploderText, where, powerText, destroyed);
        }

        return EventChat.line(
            "tempora.event.explosion",
            when,
            exploderText,
            where,
            powerText,
            destroyed,
            EventChat.closestPlayer(closestPlayerUUID, closestPlayerDistance));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }
}
