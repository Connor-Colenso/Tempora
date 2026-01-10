package com.colen.tempora.loggers.player_movement;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerMovementQueueElement extends GenericQueueElement {

    @Column(constraints = "NOT NULL")
    public String playerUUID;

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
        return TemporaEvents.PLAYER_MOVEMENT;
    }

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp);
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.player_movement",
            PlayerUtils.playerNameFromUUID(playerUUID),
            coords,
            formattedTime);
    }

}
