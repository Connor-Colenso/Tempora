package com.colen.tempora.loggers.optional;

import net.minecraft.util.IChatComponent;

public interface ISupportsUndo {

    IChatComponent undoEvent(String eventUUID);
}
