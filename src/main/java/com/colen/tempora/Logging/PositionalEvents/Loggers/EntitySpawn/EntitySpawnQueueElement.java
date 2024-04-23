package com.colen.tempora.Logging.PositionalEvents.Loggers.EntitySpawn;

import com.colen.tempora.Logging.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.colen.tempora.Utils.TimeUtils;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.entity_spawn",
            entityName,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
