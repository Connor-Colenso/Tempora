package com.colen.tempora.logging.loggers.player_interact_with_inventory;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.StatCollector;

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
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp); // Formatting the timestamp into a readable time.
        String playerName = PlayerUtils.UUIDToName(playerUUID); // Converting player UUID to name.
        String itemDetails = getNameOfItemStack(itemId, itemMetadata); // Getting name and details of the item stack.

        if (interactionType.equals("Added")) {
            return StatCollector.translateToLocalFormatted(
                "message.inventory_interaction_added",
                playerName,
                stacksize,
                itemDetails,
                itemId,
                itemMetadata,
                containerName,
                (int) x,
                (int) y,
                (int) z,
                formattedTime);
        } else {
            return StatCollector.translateToLocalFormatted(
                "message.inventory_interaction_removed",
                playerName,
                stacksize,
                itemDetails,
                itemId,
                itemMetadata,
                containerName,
                (int) x,
                (int) y,
                (int) z,
                formattedTime);
        }
    }

}
