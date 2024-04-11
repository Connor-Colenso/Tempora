package com.myname.mymodid.PositionalEvents.Loggers.BlockPlace;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerUUIDWhoPlacedBlock;

    public BlockPlaceQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

    @Override
    public String localiseText() {
        return null;
    }
}
