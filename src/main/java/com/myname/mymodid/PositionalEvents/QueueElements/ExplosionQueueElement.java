package com.myname.mymodid.PositionalEvents.QueueElements;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderName;
    public String closestPlayerUUID;
    public double closestPlayerUUIDDistance;

    public ExplosionQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
