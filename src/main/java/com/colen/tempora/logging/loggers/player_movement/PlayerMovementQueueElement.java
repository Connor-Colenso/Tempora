package com.colen.tempora.logging.loggers.player_movement;

import net.minecraft.util.StatCollector;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerName;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.player_movement",
            playerName,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
