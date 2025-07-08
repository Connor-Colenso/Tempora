package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.ItemUseLogger;
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.item_use",
            playerName,
            getNameOfItemStack(itemID, itemMetadata),
            itemID,
            itemMetadata,
            coords,
            timeAgo
        );
    }
}
