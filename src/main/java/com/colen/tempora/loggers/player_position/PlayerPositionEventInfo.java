package com.colen.tempora.loggers.player_position;

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

public class PlayerPositionEventInfo extends PositionalEventInfo {

    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_POSITION;
    }

    @Override
    public IChatComponent localiseText() {
        return EventChat.line(
            "tempora.event.player_position",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

}
