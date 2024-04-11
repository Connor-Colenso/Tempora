package com.myname.mymodid.PositionalEvents.Loggers.EntityPosition;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class EntityPositionQueueElement extends GenericQueueElement {

    public String entityName;

    public EntityPositionQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

    @Override
    public String localiseText() {
        return null;
    }
}
