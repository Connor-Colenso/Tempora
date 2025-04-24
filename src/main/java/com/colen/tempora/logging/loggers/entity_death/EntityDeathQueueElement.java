package com.colen.tempora.logging.loggers.entity_death;

import net.minecraft.util.StatCollector;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector
            .translateToLocalFormatted("message.entity_death", nameOfDeadMob, killedBy, x, y, z, formattedTime);
    }
}
