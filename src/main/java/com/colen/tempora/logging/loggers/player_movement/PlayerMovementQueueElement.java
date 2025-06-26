package com.colen.tempora.logging.loggers.player_movement;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerName;

    @Override
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.player_movement",
            playerName,   // %1$s - player name
            coords,       // %2$s - clickable coordinates
            formattedTime // %3$s - formatted time
        );
    }
}
