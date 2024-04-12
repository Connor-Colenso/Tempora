package com.myname.mymodid.PositionalEvents.Loggers.Explosion;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import net.minecraft.util.StatCollector;
import com.myname.mymodid.Utils.TimeUtils;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderName;
    public String closestPlayerName;
    public double closestPlayerDistance;

    @Override
    public String localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.explosion",
            exploderName,
            String.format("%.1f", strength),
            closestPlayerName,
            String.format("%.1f", closestPlayerDistance),
            String.format("%.1f", x),
            String.format("%.1f", y),
            String.format("%.1f", z),
            formattedTime);
    }
}