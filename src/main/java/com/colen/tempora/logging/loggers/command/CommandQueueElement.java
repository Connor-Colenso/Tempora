package com.colen.tempora.logging.loggers.command;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class CommandQueueElement extends GenericQueueElement {

    public String playerNameWhoIssuedCommand;
    public String commandName;
    public String arguments;

    @Override
    public IChatComponent localiseText(String uuid) {
        // Relative time (as chat component with hover info)
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        // Clickable coordinates with limited float precision
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.command_issued",
            playerNameWhoIssuedCommand, // %s – player name
            commandName,                // %s – command (e.g. tp)
            arguments,                  // %s – arguments
            coords,                     // %s – clickable location
            timeAgo                     // %s – relative time
        );
    }
}
