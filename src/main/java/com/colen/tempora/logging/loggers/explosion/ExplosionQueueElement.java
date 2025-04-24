package com.colen.tempora.logging.loggers.explosion;

import net.minecraft.util.StatCollector;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderUUID;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public String localiseText() {
        return String.format(
            StatCollector.translateToLocal("message.explosion"),
            exploderUUID,
            String.format("%.1f", strength),
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance),
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            TimeUtils.formatTime(timestamp));
    }
}
