package com.myname.mymodid.PositionalEvents.Loggers.Generic;

import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;

public abstract class GenericQueueElement implements ISerializable {

    public double x;
    public double y;
    public double z;
    public int dimensionId;
    public long timestamp;

}
