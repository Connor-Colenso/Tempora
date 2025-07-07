package com.colen.tempora.logging.loggers.block_change;

import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockChangeQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String stackTrace;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public IChatComponent localiseText(String uuid) {

        IChatComponent blockName = BlockUtils.getUnlocalisedChatComponent(blockID, metadata);
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.INT);
        // We use UUID to determine timezone, for localising.
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_change",
            blockName,
            coords,
            stackTrace,
            timeAgo,
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance) // %s (distance)
        );
    }
}
