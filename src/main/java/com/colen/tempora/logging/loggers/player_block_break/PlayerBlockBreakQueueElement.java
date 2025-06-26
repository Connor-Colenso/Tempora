package com.colen.tempora.logging.loggers.player_block_break;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class PlayerBlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerNameWhoBrokeBlock;

    @Override
    public IChatComponent localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        return new ChatComponentTranslation(
            "message.block_break",
            playerNameWhoBrokeBlock, // %1$s - player name
            localizedName,           // %2$s - block localized name
            blockID,                 // %3$d - block ID
            metadata,                // %4$d - metadata
            coords,                  // %5$s - clickable coordinates
            formattedTime            // %6$s - formatted time
        );
    }
}
