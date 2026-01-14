package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static cpw.mods.fml.common.network.ByteBufUtils.readVarInt;
import static cpw.mods.fml.common.network.ByteBufUtils.varIntByteCount;
import static cpw.mods.fml.common.network.ByteBufUtils.writeVarInt;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.Validate;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.google.common.base.Charsets;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ExplosionEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL DEFAULT -1")
    public float strength;
    @Column(constraints = "NOT NULL")
    public String exploderUUID;
    @Column(constraints = "NOT NULL")
    public String closestPlayerUUID;
    @Column(constraints = "NOT NULL DEFAULT -1")
    public double closestPlayerDistance;
    @Column(constraints = "NOT NULL")
    public String affectedBlockCoordinates;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        strength = buf.readFloat();
        exploderUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerDistance = buf.readDouble();
        affectedBlockCoordinates = readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeFloat(strength);
        ByteBufUtils.writeUTF8String(buf, exploderUUID);
        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
        writeUTF8String(buf, affectedBlockCoordinates);
    }

    // To bypass string encoding limits, for large quantities of affectedBlockCoordinates. Todo: review this
    public static void writeUTF8String(ByteBuf to, String string) {
        byte[] utf8Bytes = string.getBytes(Charsets.UTF_8);
        // Allow up to 4 bytes for the length varint (~268 MB of data)
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
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        return new ChatComponentTranslation(
            "message.explosion",
            PlayerUtils.playerNameFromUUID(exploderUUID),
            formatNumber(strength, ONE_DP),
            closestPlayerUUID,
            formatNumber(closestPlayerDistance, ONE_DP),
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }
}
