package com.colen.tempora.logging.loggers.generic;

import com.colen.tempora.logging.loggers.ISerializable;

public abstract class GenericQueueElement implements ISerializable {

    public double x;
    public double y;
    public double z;
    public int dimensionId;
    public long timestamp;

}
