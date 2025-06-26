package com.colen.tempora.logging.loggers.block_place;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int    blockID;
    public int    metadata;
    public String playerNameWhoPlacedBlock;

    @Override
    public IChatComponent localiseText(String uuid) {

        /*  Block name as an *unlocalized* key, wrapped so the CLIENT localises it  */
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(blockID, metadata);

        /*  Clickable coordinates component  */
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        /*  Relative-time component (hover shows exact time, already handled in TimeUtils)  */
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_place",
            playerNameWhoPlacedBlock, // %0 – player name
            block,                    // %1 – block name (localised client-side)
            blockID,                  // %2 – raw ID
            metadata,                 // %3 – meta
            coords,                   // %4 – clickable coords
            timeAgo                   // %5 – “x minutes ago”
        );
    }
}
