package com.myname.mymodid.PositionalEvents.QueueElements;

public class CommandQueueElement extends GenericQueueElement {

    public String playerWhoIssuedCommand;
    public String commandName;
    public String arguments;

    public CommandQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

}
