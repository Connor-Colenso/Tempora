package com.colen.tempora.logging.loggers.explosion;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderUUID;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public IChatComponent localiseText() {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.explosion",
            exploderUUID,                      // %1$s - UUID of exploder
            String.format("%.1f", strength),  // %2$s - explosion strength
            closestPlayerUUID,                 // %3$s - closest player UUID
            String.format("%.1f", closestPlayerDistance), // %4$s - distance to closest player
            coords,                           // %5$s - clickable coords
            TimeUtils.formatTime(timestamp)  // %6$s - formatted timestamp
        );
    }
}
