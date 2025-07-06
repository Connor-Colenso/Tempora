package com.colen.tempora.logging.loggers.explosion;

import com.colen.tempora.utils.PlayerUtils;
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
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.explosion",
            exploderUUID, // %s - UUID of exploder
            String.format("%.1f", strength), // %s - explosion strength
            closestPlayerUUID, // %s - closest player UUID
            String.format("%.1f", closestPlayerDistance), // %s - distance
            coords, // %s - clickable coords
            timeAgo // %s - relative time
        );
    }
}
