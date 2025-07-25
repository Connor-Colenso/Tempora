package com.colen.tempora.loggers.player_movement;

import com.colen.tempora.enums.LoggerEnum;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerUUID;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation("message.player_movement", playerUUID, coords, formattedTime);
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerMovementLogger;
    }
}
