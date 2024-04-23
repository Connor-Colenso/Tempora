package com.colen.tempora.Logging.PositionalEvents.Loggers.Command;

import com.colen.tempora.Logging.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.colen.tempora.Utils.TimeUtils;
import net.minecraft.util.StatCollector;

public class CommandQueueElement extends GenericQueueElement {

    public String playerNameWhoIssuedCommand;
    public String commandName;
    public String arguments;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.command_issued",
            playerNameWhoIssuedCommand,
            commandName,
            arguments,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
