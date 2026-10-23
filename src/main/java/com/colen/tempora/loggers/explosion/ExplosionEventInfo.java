package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static cpw.mods.fml.common.network.ByteBufUtils.readVarInt;
import static cpw.mods.fml.common.network.ByteBufUtils.varIntByteCount;
import static cpw.mods.fml.common.network.ByteBufUtils.writeVarInt;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.Validate;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.ChunkPositionUtils;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.google.common.base.Charsets;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ExplosionEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL DEFAULT -1")
    public float power;
    @Column(constraints = "NOT NULL")
    public String exploder = PlayerUtils.UNKNOWN_CAUSE;
    @Column(constraints = "NOT NULL")
    public String closestPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;
    @Column(constraints = "NOT NULL DEFAULT -1")
    public double closestPlayerDistance;
    @Column(constraints = "NOT NULL")
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

    // To bypass string encoding limits, for large quantities of affectedBlockCoordinates. Todo: review this
    public static void writeUTF8String(ByteBuf to, String string) {
        byte[] utf8Bytes = string.getBytes(Charsets.UTF_8);
        // Allow up to 4 bytes for the length VarInt (~268 MB of data)
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

        // A player by UUID, anything else by its EntityList name (older rows have a living entity's UUID, or a class
        // name).
        IChatComponent line;
        if (exploder == null || UNKNOWN_CAUSE.equals(exploder)) {
            line = EventChat.line("tempora.event.explosion.unknown", when, where, powerText);
        } else {
            IChatComponent exploderText = PlayerUtils.isUUID(exploder) ? EventChat.player(exploder)
                : EventChat.entityName(exploder);
            line = EventChat.line("tempora.event.explosion", when, exploderText, where, powerText);
        }

        // The rest is on hover, so the line fits.
        IChatComponent details = new ChatComponentTranslation(
            "tempora.event.explosion.destroyed",
            ChunkPositionUtils.decodePositions(affectedBlockCoordinates)
                .size());
        if (closestPlayerDistance >= 0) {
            details.appendText("\n")
                .appendSibling(
                    new ChatComponentTranslation(
                        "tempora.event.explosion.closest_player",
                        PlayerUtils.playerNameFromUUID(closestPlayerUUID),
                        formatNumber(closestPlayerDistance, ONE_DP)));
        }
        line.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, EventChat.grey(details)));
        return line;
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }
}
