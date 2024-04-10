package com.myname.mymodid.PositionalEvents.QueueElements;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerWhoPlacedBlock;

    public BlockPlaceQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
