package com.colen.tempora.loggers.command;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommandLogger extends GenericPositionalLogger<CommandEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.COMMAND;
    }

    @Override
    public @NotNull String getShortName() {
        return "command";
    }

    @Override
    public @NotNull CommandEventInfo newEventInfo() {
        return new CommandEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            logCommand(player, event.command.getCommandName(), event.parameters);
        }
    }

    /** Logs a command a player ran, at the player's position. */
    public void logCommand(EntityPlayer player, String commandName, String[] arguments) {
        if (!isEnabledByConfig) return;

        CommandEventInfo eventInfo = new CommandEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.commandName = commandName;
        eventInfo.arguments = String.join(" ", arguments);

        logEvent(eventInfo, player.dimension, player.posX, player.posY, player.posZ);
    }
}
