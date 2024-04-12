package com.myname.mymodid.PositionalEvents.Loggers.Explosion;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderName;
    public String closestPlayerName;
    public double closestPlayerDistance;

    @Override
    public String localiseText() {
        return null;
    }
}
