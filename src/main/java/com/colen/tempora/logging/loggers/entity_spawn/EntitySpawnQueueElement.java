package com.colen.tempora.logging.loggers.entity_spawn;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

import static com.colen.tempora.utils.GenericUtils.entityUUIDChatComponent;

public class EntitySpawnQueueElement extends GenericQueueElement {

    public String entityName;
    public String entityUUID;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);
        IChatComponent uuidChatComponent = entityUUIDChatComponent(entityUUID);

        return new ChatComponentTranslation(
            "message.entity_spawn",
            entityName,
            uuidChatComponent,
            coords,
            timeAgo
        );
    }
}
