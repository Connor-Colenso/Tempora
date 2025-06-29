package com.colen.tempora.logging.loggers.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

public class PlayerInteractWithInventoryQueueElement extends GenericQueueElement {

    public String containerName;
    public int interactionType;
    public int itemId;
    public int itemMetadata;
    public String playerUUID;
    public int stackSize;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        String playerName = PlayerUtils.UUIDToName(playerUUID);

        // Try localise the item name...
        ItemStack itemStack = new ItemStack(Item.getItemById(itemId), stackSize, itemMetadata);
        IChatComponent itemDetails = new ChatComponentTranslation(itemStack.getDisplayName());

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        InventoryLogger.Direction dir = InventoryLogger.Direction.fromOrdinal(interactionType);

        if (dir == null) {
            return new ChatComponentText("Error: invalid interactionType " + interactionType + " in InventoryLogger DB. Please report this.");
        }

        String translationKey = dir.isAddition()
            ? "message.inventory_interaction_added"
            : "message.inventory_interaction_removed";

        return new ChatComponentTranslation(
            translationKey,
            playerName,      // %1$s - player name
            stackSize,       // %2$d - stack size
            itemDetails,     // %3$s - item name/details (IChatComponent)
            itemId,          // %4$d - item ID
            itemMetadata,    // %5$d - item metadata
            containerName,   // %6$s - container name
            coords,          // %7$s - clickable coordinates (IChatComponent)
            formattedTime    // %8$s - localized relative time (IChatComponent)
        );
    }
}
