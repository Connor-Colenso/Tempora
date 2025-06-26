package com.colen.tempora.logging.loggers.player_interact_with_inventory;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.colen.tempora.utils.ItemUtils;

public class PlayerInteractWithInventoryQueueElement extends GenericQueueElement {

    public String containerName;
    public String interactionType;
    public int itemId;
    public int itemMetadata;
    public String playerUUID;
    public int stacksize;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        String playerName = PlayerUtils.UUIDToName(playerUUID);

        // Create an IChatComponent for the item details - if no such method exists, fallback to plain text:
        IChatComponent itemDetails = new ChatComponentText(ItemUtils.getNameOfItemStack(itemId, itemMetadata));

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        String translationKey = "Added".equals(interactionType)
            ? "message.inventory_interaction_added"
            : "message.inventory_interaction_removed";

        return new ChatComponentTranslation(
            translationKey,
            playerName,      // %1$s - player name
            stacksize,       // %2$d - stack size
            itemDetails,     // %3$s - item name/details (IChatComponent)
            itemId,          // %4$d - item ID
            itemMetadata,    // %5$d - item metadata
            containerName,   // %6$s - container name
            coords,          // %7$s - clickable coordinates (IChatComponent)
            formattedTime    // %8$s - localized relative time (IChatComponent)
        );
    }
}
