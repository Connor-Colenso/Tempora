package com.colen.tempora.loggers.command;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class CommandEventInfo extends PositionalEventInfo {

    // Commands longer than this are cut short in chat.
    private static final int MAX_SHOWN_LENGTH = 40;

    @Column(constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public String commandName = "";

    @Column(constraints = "NOT NULL")
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

        // Long commands are cut short so the line fits, the whole of it is on hover. Clicking puts it in the chat box.
        boolean cut = command.length() > MAX_SHOWN_LENGTH;
        IChatComponent shown = EventChat
            .text(cut ? command.substring(0, MAX_SHOWN_LENGTH - 3) + "..." : command, EnumChatFormatting.WHITE);

        IChatComponent hover = new ChatComponentText(cut ? command + "\n" : "");
        hover.appendSibling(new ChatComponentTranslation("tempora.event.command.hover"));
        shown.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, EventChat.grey(hover)))
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
