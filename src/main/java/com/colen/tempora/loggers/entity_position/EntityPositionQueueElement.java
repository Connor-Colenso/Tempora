package com.colen.tempora.loggers.entity_position;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class EntityPositionQueueElement extends GenericQueueElement {

    public String entityName;
    public String entityUUID;
    public float rotationYaw;
    public float rotationPitch;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        entityName = ByteBufUtils.readUTF8String(buf);
        entityUUID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, entityName);
        ByteBufUtils.writeUTF8String(buf, entityUUID);
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        IChatComponent clickToCopy = new ChatComponentTranslation("tempora.click.to.copy.uuid");
        clickToCopy.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        IChatComponent uuidChatComponent = new ChatComponentText("[UUID]").setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, entityUUID))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, clickToCopy)));

        return new ChatComponentTranslation(
            "message.entity_position",
            new ChatComponentTranslation("entity." + entityName + ".name"),
            uuidChatComponent,
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ENTITY_POSITION;
    }
}
