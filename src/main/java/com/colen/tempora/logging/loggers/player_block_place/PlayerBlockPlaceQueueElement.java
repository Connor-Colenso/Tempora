package com.colen.tempora.logging.loggers.player_block_place;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class PlayerBlockPlaceQueueElement extends GenericQueueElement {
    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String playerNameWhoPlacedBlock;

    @Override
    public IChatComponent localiseText(String uuid) {
        // Use normalized pickBlockID and pickBlockMeta for translation key
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);

        // Clickable coords component
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        // Relative time component
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        // Create translation with playerName, block, raw blockID:metadata, coords, and timeAgo
        return new ChatComponentTranslation(
            "message.block_place",
            playerNameWhoPlacedBlock, // %0 – player name
            block,                    // %1 – block name (localized client-side)
            blockID,                  // %2 – raw block ID
            metadata,                 // %3 – raw metadata
            coords,                   // %4 – clickable coords
            timeAgo                   // %5 – relative time
        );
    }
}

