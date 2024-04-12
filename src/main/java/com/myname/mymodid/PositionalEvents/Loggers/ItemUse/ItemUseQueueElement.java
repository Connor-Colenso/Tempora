package com.myname.mymodid.PositionalEvents.Loggers.ItemUse;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    @Override
    public String localiseText() {
        return null;
    }
}
