package com.colen.tempora.logging.loggers.entity_death;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntityDeathQueueElement extends GenericQueueElement {

    public String nameOfDeadMob;
    public String killedBy;

    @Override
    public IChatComponent localiseText() {
        String formattedTime = TimeUtils.formatTime(timestamp);

        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);

        return new ChatComponentTranslation(
            "message.entity_death",
            nameOfDeadMob,   // %1$s - mob name
            killedBy,        // %2$s - killer name
            coords,          // %3$s - clickable coordinates
            formattedTime    // %4$s - formatted time
        );
    }
}
