package com.myname.mymodid.PositionalEvents.Loggers.ItemUse;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerUUID;
    public int itemID;
    public int itemMetadata;

    public ItemUseQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
