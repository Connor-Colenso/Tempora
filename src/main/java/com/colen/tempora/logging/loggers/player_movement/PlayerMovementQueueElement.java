package com.colen.tempora.logging.loggers.player_movement;

import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerName;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.player_movement",
            playerName, // %1$s - player name
            coords, // %2$s - clickable coordinates
            formattedTime // %3$s - localized formatted time
        );
    }
}
