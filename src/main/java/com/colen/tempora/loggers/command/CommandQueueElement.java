package com.colen.tempora.loggers.command;

import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class CommandQueueElement extends GenericQueueElement {

    public String playerUUID;
    public String commandName;
    public String arguments;

    @Override
    public IChatComponent localiseText(String uuid) {
        // Relative time (as chat component with hover info)
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        // Clickable coordinates with limited float precision
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.command_issued",
            PlayerUtils.UUIDToName(playerUUID),
            commandName,
            arguments,
            coords,
            timeAgo
        );
    }
}
