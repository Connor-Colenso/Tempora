package com.colen.tempora.commands;

import java.util.List;

import com.colen.tempora.TemporaLoggerManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
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
        if (genericLogger instanceof ISupportsUndo supportsUndo) {
            GenericQueueElement queueElement = genericLogger.db.queryEventByEventID(eventID);
            IChatComponent chatMessage = supportsUndo.undoEvent(queueElement);
            sender.addChatMessage(chatMessage);
        } else if (genericLogger == null) {
            throw new WrongUsageException("tempora.command.undo.wrong.logger", loggerName);
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
