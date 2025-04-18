package com.colen.tempora.logging.loggers.block_place;

import net.minecraft.util.StatCollector;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerNameWhoPlacedBlock;

    @Override
    public String localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.block_place",
            playerNameWhoPlacedBlock,
            localizedName,
            blockID,
            metadata,
            Math.round(x),
            Math.round(y),
            Math.round(z),
            formattedTime);
    }
}
