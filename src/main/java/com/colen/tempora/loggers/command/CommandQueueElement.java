package com.colen.tempora.loggers.command;

import com.colen.tempora.loggers.generic.Column;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class CommandQueueElement extends GenericQueueElement {

    @Column(type="TEXT", constraints = "NOT NULL")
    public String playerUUID;

    @Column(type="TEXT", constraints = "NOT NULL")
    public String commandName;

    @Column(type="TEXT", constraints = "NOT NULL")
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
    public IChatComponent localiseText(String uuid) {
        // Relative time (as chat component with hover info)
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        // Clickable coordinates with limited float precision
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionID,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);

        return new ChatComponentTranslation(
            "message.command_issued",
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
