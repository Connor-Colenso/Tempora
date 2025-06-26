package com.colen.tempora.logging.loggers.player_interact_with_inventory;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

public class PlayerInteractWithInventoryQueueElement extends GenericQueueElement {

    public String containerName;
    public String interactionType;
    public int itemId;
    public int itemMetadata;
    public String playerUUID;
    public int stacksize;

    @Override
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);
        String playerName = PlayerUtils.UUIDToName(playerUUID);
        String itemDetails = getNameOfItemStack(itemId, itemMetadata);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        String translationKey;
        if ("Added".equals(interactionType)) {
            translationKey = "message.inventory_interaction_added";
        } else {
            translationKey = "message.inventory_interaction_removed";
        }

        return new ChatComponentTranslation(
            translationKey,
            playerName,      // %1$s - player name
            stacksize,       // %2$d - stack size
            itemDetails,     // %3$s - item name/details
            itemId,          // %4$d - item ID
            itemMetadata,    // %5$d - item metadata
            containerName,   // %6$s - container name
            coords,          // %7$s - clickable coordinates
            formattedTime    // %8$s - formatted time
        );
    }
}
