package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;
import com.myname.mymodid.Utils.BlockUtils;
import com.myname.mymodid.Utils.TimeUtils;

public class BlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerUUIDWhoBrokeBlock;

    public BlockBreakQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

    public BlockBreakQueueElement() { }

    @Override
    public String localiseText() {
        return playerUUIDWhoBrokeBlock + " broke " + blockID + ":" + metadata + " at [" + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z) + "] at " + TimeUtils.formatTime(timestamp);
    }
}
