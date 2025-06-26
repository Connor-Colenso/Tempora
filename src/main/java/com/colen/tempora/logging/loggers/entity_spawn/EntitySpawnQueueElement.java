package com.colen.tempora.logging.loggers.entity_spawn;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;

    @Override
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.entity_spawn",
            entityName,   // %1$s - entity name
            coords,       // %2$s - clickable coordinates
            formattedTime // %3$s - formatted time
        );
    }
}
