package com.colen.tempora.commands;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.UndoResponse;
import com.colen.tempora.utils.ChatUtils;
import com.colen.tempora.utils.CommandUtils;

public class TemporaUndoCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "tempora_undo";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_undo <logger_name> <event_id>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP‑only by default
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException(getCommandUsage(sender));

        String loggerName = args[0];
        String eventID = args[1];

        GenericPositionalLogger<?> genericLogger = TemporaLoggerManager.getLogger(loggerName);
        if (genericLogger == null) {
            throw new WrongUsageException("tempora.command.undo.wrong.logger", loggerName);
        } else if (genericLogger.isUndoEnabled()) {
            GenericEventInfo eventInfo = genericLogger.getDatabaseManager()
                .queryEventByEventID(eventID);
            UndoResponse undoResponse = genericLogger.undoEvent(eventInfo, (EntityPlayer) sender);

            // Tell the user the response from the undo command.
            if (undoResponse != null && undoResponse.message != null && undoResponse.success != null) {
                sender.addChatMessage(undoResponse.message);
            } else {
                // Something gone wrong with the undo implementation. This may not be tempora's fault, depending on the
                // origin of this logger.
                IChatComponent errorMsg = new ChatComponentTranslation(
                    "tempora.command.undo.failed.bad.implementation",
                    loggerName,
                    ChatUtils.createHoverableClickable("[UUID]", eventID));
                errorMsg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(errorMsg);
            }
        } else {
            throw new WrongUsageException("tempora.command.undo.not_undoable", loggerName);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null;
    }
}
