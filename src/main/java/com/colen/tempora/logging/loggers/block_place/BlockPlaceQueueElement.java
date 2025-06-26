package com.colen.tempora.logging.loggers.block_place;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerNameWhoPlacedBlock;

    @Override
    public IChatComponent localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords =
            generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        return new ChatComponentTranslation(
            "message.block_place",
            playerNameWhoPlacedBlock,   // %1$s – player name
            localizedName,              // %2$s – block name
            blockID,                    // %3$d – id
            metadata,                   // %4$d – meta
            coords,                     // %5$s – clickable “123 64 -217”
            formattedTime               // %6$s – date/time
        );
    }
}
