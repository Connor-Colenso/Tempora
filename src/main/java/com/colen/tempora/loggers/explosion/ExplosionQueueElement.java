package com.colen.tempora.loggers.explosion;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderUUID;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        strength = buf.readFloat();
        exploderUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerDistance = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeFloat(strength);
        ByteBufUtils.writeUTF8String(buf, exploderUUID);
        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
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

        return new ChatComponentTranslation(
            "message.explosion",
            exploderUUID,
            String.format("%.1f", strength),
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance),
            coords,
            timeAgo);
    }
}
