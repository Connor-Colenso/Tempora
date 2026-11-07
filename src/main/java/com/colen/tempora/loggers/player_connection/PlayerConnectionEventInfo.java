package com.colen.tempora.loggers.player_connection;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.GlobalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// Player joining or leaving and where they were at the time
public class PlayerConnectionEventInfo extends GlobalEventInfo {

    // A PlayerConnectionLogger.Action
    @Column
    public int action;

    @Column(type = ColumnType.PLAYER)
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    // Why a player left, a translation key like disconnect.timeout or the kick message text. Empty for joins
    @Column
    public String reason = "";

    @Column
    public int dimensionID;

    @Column
    public double x;

    @Column
    public double y;

    @Column
    public double z;

    @Override
    public IChatComponent localiseText() {
        PlayerConnectionLogger.Action act = PlayerConnectionLogger.Action.fromDbId(action);
        if (act == null) {
            IChatComponent invalid = new ChatComponentTranslation(
                "tempora.event.player_connection.invalid",
                new ChatComponentNumber(action));
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

        // Kick messages shown as is not as a translation, so a % in one can't break the line
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
