package com.myname.mymodid.PositionalEvents.Loggers.BlockPlace;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class BlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerUUIDWhoPlacedBlock;

    @Override
    public String localiseText() {
        return null;
    }
}
