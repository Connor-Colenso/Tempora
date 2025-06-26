package com.colen.tempora.logging.loggers.entity_death;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords   = generateTeleportChatComponent(x, y, z, CoordFormat.INT);
        IChatComponent timeAgo  = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.entity_death",
            nameOfDeadMob,  // %s – mob name
            killedBy,       // %s – killer
            coords,         // %s – clickable coords
            timeAgo         // %s – relative time
        );
    }
}
