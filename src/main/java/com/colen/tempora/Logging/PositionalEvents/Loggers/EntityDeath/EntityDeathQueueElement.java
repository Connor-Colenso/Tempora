package com.colen.tempora.Logging.PositionalEvents.Loggers.EntityDeath;

import com.colen.tempora.Logging.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.colen.tempora.Utils.TimeUtils;

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
