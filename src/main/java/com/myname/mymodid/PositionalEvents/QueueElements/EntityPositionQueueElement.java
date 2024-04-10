package com.myname.mymodid.PositionalEvents.QueueElements;

public class EntityPositionQueueElement extends GenericQueueElement {

    public String entityName;

    public EntityPositionQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
