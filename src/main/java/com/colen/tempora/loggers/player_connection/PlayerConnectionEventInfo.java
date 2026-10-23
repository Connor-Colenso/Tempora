package com.colen.tempora.loggers.player_connection;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.GlobalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

/** A player joining or leaving the server, and where they were at the time. */
public class PlayerConnectionEventInfo extends GlobalEventInfo {

    // A PlayerConnectionLogger.Action.
    @Column(constraints = "NOT NULL")
    public int action;

    @Column(constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    // Why a player left: a translation key such as disconnect.timeout, or the text of a kick message. Empty for joins.
    @Column(constraints = "NOT NULL")
    public String reason = "";

    @Column(constraints = "NOT NULL")
    public int dimensionID;

    @Column(constraints = "NOT NULL")
    public double x;

    @Column(constraints = "NOT NULL")
    public double y;

    @Column(constraints = "NOT NULL")
    public double z;

    @Override
    public IChatComponent localiseText() {
        PlayerConnectionLogger.Action act = PlayerConnectionLogger.Action.fromDbId(action);
        if (act == null) {
            IChatComponent invalid = new ChatComponentTranslation("tempora.event.player_connection.invalid", action);
            invalid.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            return invalid;
        }

        IChatComponent when = EventChat.time(timestamp);
        IChatComponent who = EventChat.player(playerUUID);
        IChatComponent where = CommandUtils
            .teleportChatComponent(x, y, z, dimensionID, CommandUtils.TeleportType.EXACT);

        if (act == PlayerConnectionLogger.Action.JOIN) {
            return EventChat.line(act.langKey(), when, who, where);
        }

        // A kick message is shown as it is, rather than as a translation, so a % in it cannot break the line.
        IChatComponent reasonText = StatCollector.canTranslate(reason) ? new ChatComponentTranslation(reason)
            : EventChat.text(reason, EnumChatFormatting.WHITE);
        reasonText.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        return EventChat.line(act.langKey(), when, who, where, reasonText);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_CONNECTION;
    }
}
