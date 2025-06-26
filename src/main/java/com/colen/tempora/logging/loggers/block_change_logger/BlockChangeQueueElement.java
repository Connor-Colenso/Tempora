package com.colen.tempora.logging.loggers.block_change_logger;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockChangeQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String stackTrace;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public IChatComponent localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        return new ChatComponentTranslation(
            "message.block_change",
            localizedName,
            generateTeleportChatComponent(x, y, z, CoordFormat.INT),
            formattedTime,
            stackTrace,
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance)
        );
    }
}
