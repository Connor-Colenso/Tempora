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
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.command_issued",
            playerNameWhoIssuedCommand, // %1$s - player name
            commandName,                // %2$s - command name
            arguments,                  // %3$s - arguments
            coords,                     // %4$s - clickable coords (instead of raw x,y,z)
            formattedTime               // %5$s - time
        );
    }
}
