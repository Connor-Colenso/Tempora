package com.colen.tempora.loggers.optional;

import java.util.List;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;

public interface ISupportsUndo {

    IChatComponent undoEvent(GenericQueueElement queueElement);

    default void undoEvents(List<GenericQueueElement> results) {
        for (GenericQueueElement element : results) {
            undoEvent(element);
        }
    }
}
