package com.colen.tempora.loggers.entity_position;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.PlayerUtils;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.TimeUtils;

public class EntityPositionQueueElement extends GenericQueueElement {

    public String entityName;
    public String entityUUID;

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntityPositionLogger;
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid), CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        IChatComponent clickToCopy = new ChatComponentTranslation("tempora.click.to.copy.uuid");
        clickToCopy.getChatStyle().setColor(EnumChatFormatting.GRAY);

        IChatComponent uuidChatComponent = new ChatComponentText("[UUID]")
            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, entityUUID))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, clickToCopy)));

        return new ChatComponentTranslation(
            "message.entity_position",
            entityName,
            uuidChatComponent,
            coords,
            timeAgo
        );
    }
}
