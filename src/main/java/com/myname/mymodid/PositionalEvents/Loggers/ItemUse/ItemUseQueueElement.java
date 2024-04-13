package com.myname.mymodid.PositionalEvents.Loggers.ItemUse;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.myname.mymodid.Utils.TimeUtils;

import static com.myname.mymodid.Utils.ItemUtils.getNameOfItemStack;

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
