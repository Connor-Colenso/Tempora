package com.colen.tempora.PositionalEvents.Loggers.PlayerInteractWithInventory;

import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class PlayerInteractWithInventoryQueueElement extends GenericQueueElement {
    public String containerName;
    public String interactionType;
    public int itemId;
    public int itemMetadata;

    @Override
    public String localiseText() {
        return "Test";
    }
}
