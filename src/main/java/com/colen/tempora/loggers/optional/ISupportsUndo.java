package com.colen.tempora.loggers.optional;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import net.minecraft.util.IChatComponent;

import java.util.List;

public interface ISupportsUndo {

    IChatComponent undoEvent(GenericQueueElement queueElement);

    default void undoEvents(List<GenericQueueElement> results) {
        for (GenericQueueElement element : results) {
            undoEvent(element);
        }
    }
}
