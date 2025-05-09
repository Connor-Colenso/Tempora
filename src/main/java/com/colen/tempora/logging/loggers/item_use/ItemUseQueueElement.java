package com.colen.tempora.logging.loggers.item_use;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.StatCollector;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.item_use",
            playerName,
            getNameOfItemStack(itemID, itemMetadata),
            itemID,
            itemMetadata,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
