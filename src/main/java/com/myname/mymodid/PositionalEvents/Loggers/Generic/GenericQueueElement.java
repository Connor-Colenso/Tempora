package com.myname.mymodid.PositionalEvents.Loggers.Generic;

public abstract class GenericQueueElement {

    public double x;
    public double y;
    public double z;
    public int dimensionId;
    public long timestamp;

    public GenericQueueElement(double x, double y, double z, int dimensionId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionId = dimensionId;
        this.timestamp = System.currentTimeMillis();
    }

    public GenericQueueElement() {

    }

}
