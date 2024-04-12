package com.myname.mymodid.PositionalEvents.Loggers.PlayerMovement;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.myname.mymodid.Utils.TimeUtils;

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
