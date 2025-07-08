package com.colen.tempora.loggers.player_movement;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerUUID;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerMovementLogger;
    }


    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.player_movement",
            playerUUID,
            coords,
            formattedTime
        );
    }
}
