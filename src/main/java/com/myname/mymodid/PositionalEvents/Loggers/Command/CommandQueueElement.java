package com.myname.mymodid.PositionalEvents.Loggers.Command;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class CommandQueueElement extends GenericQueueElement {

    public String playerUUIDWhoIssuedCommand;
    public String commandName;
    public String arguments;

    public CommandQueueElement(double x, double y, double z, int dimensionId) {
        super(x, y, z, dimensionId);
    }

}
