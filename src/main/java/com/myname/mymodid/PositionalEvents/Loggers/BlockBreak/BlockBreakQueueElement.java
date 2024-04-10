package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class BlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerUUIDWhoBrokeBlock;

    public BlockBreakQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

}
