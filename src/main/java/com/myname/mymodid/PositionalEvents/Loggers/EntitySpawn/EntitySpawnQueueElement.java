package com.myname.mymodid.PositionalEvents.Loggers.EntitySpawn;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;

    public EntitySpawnQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
