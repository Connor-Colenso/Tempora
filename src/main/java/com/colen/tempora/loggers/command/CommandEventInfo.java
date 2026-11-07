package com.colen.tempora.loggers.command;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class CommandEventInfo extends PositionalEventInfo {

    // Commands longer than this get cut short in chat
    private static final int MAX_SHOWN_LENGTH = 40;

    @Column(type = ColumnType.PLAYER)
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column
    public String commandName = "";

    @Column
    public String arguments = "";

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
        commandName = ByteBufUtils.readUTF8String(buf);
        arguments = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        ByteBufUtils.writeUTF8String(buf, commandName);
        ByteBufUtils.writeUTF8String(buf, arguments);
    }

    @Override
    public IChatComponent localiseText() {
        String command = "/" + commandName + (arguments.isEmpty() ? "" : " " + arguments);

        // Cut long commands so the line fits, the full thing is on hover. Clicking puts it in the chat box
        boolean cut = command.length() > MAX_SHOWN_LENGTH;
        IChatComponent shown = EventChat
            .text(cut ? command.substring(0, MAX_SHOWN_LENGTH - 3) + "..." : command, EnumChatFormatting.WHITE);

        IChatComponent clickToCopy = new ChatComponentTranslation("tempora.event.command.hover");
        IChatComponent hover = cut
            ? EventChat.titledHover(EventChat.text(command, EnumChatFormatting.WHITE), clickToCopy)
            : EventChat.grey(clickToCopy);
        shown.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));

        return EventChat.line(
            "tempora.event.command",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            shown,
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.COMMAND;
    }
}
