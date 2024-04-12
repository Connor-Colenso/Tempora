package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.myname.mymodid.Utils.BlockUtils;
import com.myname.mymodid.Utils.TimeUtils;
import net.minecraft.util.StatCollector;

public class BlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerUUIDWhoBrokeBlock;

    @Override
    public String localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted("message.block_break", playerUUIDWhoBrokeBlock, localizedName, Math.round(x), Math.round(y), Math.round(z), formattedTime);
    }
}
