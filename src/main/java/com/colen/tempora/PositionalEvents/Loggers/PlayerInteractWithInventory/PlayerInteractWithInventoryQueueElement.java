package com.colen.tempora.PositionalEvents.Loggers.PlayerInteractWithInventory;

import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.colen.tempora.Utils.PlayerUtils;
import com.colen.tempora.Utils.TimeUtils;
import net.minecraft.util.StatCollector;

import static com.colen.tempora.Utils.ItemUtils.getNameOfItemStack;

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

        String interactionLocalized = interactionType.equals("Add") ? "added" : "removed";

        return StatCollector.translateToLocalFormatted(
            "message.inventory_interaction",
            playerName,
            interactionLocalized,
            stacksize,
            itemDetails,
            itemId,
            itemMetadata,
            containerName,
            (int) x,
            (int) y,
            (int) z,
            formattedTime
        );
    }


}
