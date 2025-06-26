package com.colen.tempora.logging.loggers.entity_spawn;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords   = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo  = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.entity_spawn",
            entityName,  // %s - entity name
            coords,      // %s - clickable coordinates
            timeAgo      // %s - relative time
        );
    }
}
