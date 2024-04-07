package com.myname.mymodid.QueueElement;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    public ItemUseQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
