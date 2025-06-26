package com.colen.tempora.logging.loggers.item_use;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    @Override
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.item_use",
            playerName,                   // %1$s - player name
            getNameOfItemStack(itemID, itemMetadata), // %2$s - item display name
            itemID,                      // %3$d - item ID
            itemMetadata,                // %4$d - item metadata
            coords,                      // %5$s - clickable coordinates
            formattedTime                // %6$s - formatted time
        );
    }
}
