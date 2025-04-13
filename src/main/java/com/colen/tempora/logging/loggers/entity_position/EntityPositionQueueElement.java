package com.colen.tempora.logging.loggers.entity_position;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.colen.tempora.utils.TimeUtils;

public class EntityPositionQueueElement extends GenericQueueElement {

    public String entityName;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.entity_position",
            entityName,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
