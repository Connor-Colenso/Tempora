package com.colen.tempora.Logging.PositionalEvents.Loggers.PlayerMovement;

import com.colen.tempora.Logging.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.colen.tempora.Utils.TimeUtils;

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
