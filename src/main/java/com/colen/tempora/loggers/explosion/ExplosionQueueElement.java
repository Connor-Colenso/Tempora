package com.colen.tempora.loggers.explosion;

import static cpw.mods.fml.common.network.ByteBufUtils.readVarInt;
import static cpw.mods.fml.common.network.ByteBufUtils.varIntByteCount;
import static cpw.mods.fml.common.network.ByteBufUtils.writeVarInt;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.Validate;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.Column;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.google.common.base.Charsets;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ExplosionQueueElement extends GenericQueueElement {

    @Column(type = "REAL", constraints = "NOT NULL DEFAULT -1")
    public float strength;
    @Column(type = "TEXT", constraints = "NOT NULL")
    public String exploderUUID;
    @Column(type = "TEXT", constraints = "NOT NULL")
    public String closestPlayerUUID;
    @Column(type = "REAL", constraints = "NOT NULL DEFAULT -1")
    public double closestPlayerDistance;
    @Column(type = "TEXT", constraints = "NOT NULL")
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
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionID,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        return new ChatComponentTranslation(
            "message.explosion",
            exploderUUID,
            String.format("%.1f", strength),
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance),
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }
}
