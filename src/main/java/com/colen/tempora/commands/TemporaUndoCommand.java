package com.colen.tempora.commands;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;

public class TemporaUndoCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "tempora_undo";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_undo logger_name event_id";
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
            String unlocalisedResponse = supportsUndo.undoEvent(eventID);
            sender.addChatMessage(new ChatComponentTranslation(unlocalisedResponse));
        } else {
            throw new WrongUsageException("tempora.command.undo.not_undoable", loggerName);
        }
    }
}
