package com.colen.tempora.logging.loggers.block_change_logger;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockChangeQueueElement extends GenericQueueElement {

    public int    blockID;
    public int    metadata;
    public String stackTrace;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public IChatComponent localiseText(String uuid) {

        IChatComponent blockName = BlockUtils.getUnlocalisedChatComponent(blockID, metadata);
        IChatComponent coords    = generateTeleportChatComponent(x, y, z, CoordFormat.INT);
        // We use UUID to determine timezone, for localising.
        IChatComponent timeAgo   = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_change",
            blockName,                               // %s  (block name, localised client-side)
            coords,                                  // %s  (clickable coordinates)
            stackTrace,                              // %s  (who/what set the block)
            timeAgo,                                 // %s  (relative time component)
            closestPlayerUUID,                       // %s  (nearest player)
            String.format("%.1f", closestPlayerDistance) // %s  (distance)
        );
    }
}
