package com.colen.tempora.loggers.command;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.TextFilter;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommandLogger extends GenericPositionalLogger<CommandEventInfo> {

    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.COMMAND;
    }

    @Override
    public @NotNull String getShortName() {
        return "command";
    }

    // f: matches the command as chat shows it, so f:tp finds /tp Colen. Not lookups for the same text though, or the
    // lookup being run would always find itself. Only /tempora ones, /me waves f:tpx still matches f:tpx
    @Override
    public @Nullable String textFilterCondition(TextFilter filter) {
        String matches = filter.textCondition("'/' || commandName || ' ' || arguments");
        if (matches == null) return null;
        return matches + " AND (commandName <> '"
            + TemporaCommand.NAME
            + "' OR "
            + filter.notSearchedIn("arguments")
            + ")";
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
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            logCommand(player, event.command.getCommandName(), event.parameters);
        }
    }

    // Logs a command a player ran, at their position
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
