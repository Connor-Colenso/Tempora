package com.colen.tempora.logging.loggers.entity_death;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.colen.tempora.utils.TimeUtils;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String nameOfPlayerWhoKilledMob;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.entity_death",
            nameOfDeadMob,
            nameOfPlayerWhoKilledMob,
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}
