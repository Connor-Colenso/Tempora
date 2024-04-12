package com.myname.mymodid.PositionalEvents.Loggers.Command;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.myname.mymodid.Utils.TimeUtils;

public class CommandQueueElement extends GenericQueueElement {

    public String playerNameWhoIssuedCommand;
    public String commandName;
    public String arguments;

    @Override
    public String localiseText() {
        return playerNameWhoIssuedCommand + " issued /"
            + commandName
            + " "
            + arguments
            + " at ["
            + x
            + ", "
            + y
            + ", "
            + z
            + "] "
            + TimeUtils.formatTime(timestamp);
    }
}
