package com.colen.tempora.PositionalEvents.Loggers.BlockPlace;

import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.colen.tempora.Utils.BlockUtils;
import com.colen.tempora.Utils.TimeUtils;
import net.minecraft.util.StatCollector;

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
