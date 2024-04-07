package com.myname.mymodid.QueueElement;

public class ExplosionQueueElement extends GenericQueueElement {
    public float strength;
    public String exploderName;
    public String closestPlayerName;
    public double closestPlayerDistance;

    public ExplosionQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
