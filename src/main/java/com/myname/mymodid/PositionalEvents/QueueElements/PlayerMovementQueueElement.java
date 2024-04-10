package com.myname.mymodid.PositionalEvents.QueueElements;

public class PlayerMovementQueueElement extends GenericQueueElement {

    public String playerName;

    public PlayerMovementQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }
}
