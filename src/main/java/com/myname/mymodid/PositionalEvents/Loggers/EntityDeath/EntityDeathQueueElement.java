package com.myname.mymodid.PositionalEvents.Loggers.EntityDeath;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;

    public EntityDeathQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

    @Override
    public String localiseText() {
        return null;
    }
}
