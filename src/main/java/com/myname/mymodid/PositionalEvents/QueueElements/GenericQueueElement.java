package com.myname.mymodid.PositionalEvents.QueueElements;

public abstract class GenericQueueElement {

    public final double x;
    public final double y;
    public final double z;
    public final int dimensionId;
    public final long timestamp;

    public GenericQueueElement(double x, double y, double z, int dimensionId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionId = dimensionId;
        this.timestamp = System.currentTimeMillis();
    }
}
