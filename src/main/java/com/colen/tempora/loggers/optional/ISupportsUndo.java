package com.colen.tempora.loggers.optional;

public interface ISupportsUndo {

    boolean undoEvent(String eventUUID);
}
