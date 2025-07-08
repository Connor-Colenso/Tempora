package com.colen.tempora.loggers.explosion;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class ExplosionQueueElement extends GenericQueueElement {

    public float strength;
    public String exploderUUID;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.ExplosionLogger;
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.explosion",
            exploderUUID,
            String.format("%.1f", strength),
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance),
            coords,
            timeAgo
        );
    }
}
