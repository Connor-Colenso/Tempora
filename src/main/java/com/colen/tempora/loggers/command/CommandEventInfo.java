package com.colen.tempora.loggers.command;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class CommandEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public String playerUUID;

    @Column(constraints = "NOT NULL")
    public String commandName;

    @Column(constraints = "NOT NULL")
    public String arguments;

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
    public boolean needsTransparencyToRender() {
        return true;
    }

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        // Relative time (as chat component with hover info)
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        // Clickable coordinates with limited float precision
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID, CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.command_issued",
            PlayerUtils.playerNameFromUUID(playerUUID),
            commandName,
            arguments,
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.COMMAND;
    }
}
