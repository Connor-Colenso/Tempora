package com.colen.tempora.Logging.PositionalEvents.Loggers.Generic;

// Implement this onto a relevant class to return a custom QueueElement for storing in the database.
// E.g.
// TileEntity
// Item
// Entity

public interface ITemporaCustomQueueElement<QueueElement extends GenericQueueElement> {

    QueueElement generateCustomQueueElement();

}
