package com.myname.mymodid.PositionalEvents.Loggers.EntityDeath;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;

    @Override
    public String localiseText() {
        return null;
    }
}
