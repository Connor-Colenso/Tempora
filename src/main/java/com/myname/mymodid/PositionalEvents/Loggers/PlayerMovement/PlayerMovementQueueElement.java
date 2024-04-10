package com.myname.mymodid.PositionalEvents.Loggers.PlayerMovement;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerUUID;

    public PlayerMovementQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
