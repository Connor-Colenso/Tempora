package com.colen.tempora.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;

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

        GenericPositionalLogger<?> genericLogger = GenericPositionalLogger.getLogger(loggerName);
        if (genericLogger instanceof ISupportsUndo supportsUndo) {
            GenericQueueElement queueElement = genericLogger.queryEventByEventID(eventID);
            IChatComponent chatMessage = supportsUndo.undoEvent(queueElement);
            sender.addChatMessage(chatMessage);
        } else if (genericLogger == null) {
            throw new WrongUsageException("tempora.command.undo.wrong.logger", loggerName);
        } else {
            throw new WrongUsageException("tempora.command.undo.not_undoable", loggerName);
        }
    }

    // Todo reduce code duplication here across multiple classes.
    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            String partialFilter = args[0].toLowerCase();
            List<String> matchingOptions = new ArrayList<>();
            for (String option : GenericPositionalLogger.getAllLoggerNames()) {
                if (option.toLowerCase()
                    .startsWith(partialFilter)) {
                    matchingOptions.add(option);
                }
            }
            return matchingOptions;
        }
        return null; // Return null when there are no matches.
    }
}
