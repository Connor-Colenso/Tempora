package com.colen.tempora.loggers.achievement;

import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.lookups.AchievementNames;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

// Player earning an achievement, at where they stood
public class AchievementEventInfo extends PositionalEventInfo {

    @Column(type = ColumnType.PLAYER)
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    // Achievement stat ID, e.g. achievement.mineWood
    @Column(nameRole = AchievementNames.class)
    public String achievementID = "";

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
        achievementID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        ByteBufUtils.writeUTF8String(buf, achievementID);
    }

    // The achievement, or null if no mod adds it any more
    public @Nullable Achievement achievement() {
        StatBase stat = StatList.func_151177_a(achievementID);
        return stat instanceof Achievement achievement ? achievement : null;
    }

    @Override
    public IChatComponent localiseText() {
        // Shown like vanilla announces one, [Name] with the description on hover, translated on the client. Missing
        // ones show by ID
        Achievement achievement = achievement();
        IChatComponent name = achievement != null ? achievement.func_150955_j()
            : EventChat.text("[" + achievementID + "]", EnumChatFormatting.WHITE);

        return EventChat.line(
            "tempora.event.achievement",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            EventChat.searchable(name),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ACHIEVEMENT;
    }
}
