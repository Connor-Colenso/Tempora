package com.colen.tempora.commands;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.undo.UndoEventInfo;
import com.colen.tempora.utils.ChatUtils;
import com.colen.tempora.utils.CommandUtils;

public class TemporaUndoCommand extends TemporaCommandBase {

    @Override
    public String getCommandName() {
        return "tempora_undo";
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
            throw new WrongUsageException("tempora.undo.wrong_logger", loggerName);
        } else if (genericLogger.isUndoEnabled()) {
            GenericEventInfo eventInfo = genericLogger.getDatabaseManager()
                .queryEventByEventID(eventID);
            UndoEventInfo undoEventInfo = genericLogger.undoEvents(eventInfo, (EntityPlayer) sender);

            // Tell the user the response from the undo command.
            if (undoEventInfo != null && undoEventInfo.message != null && undoEventInfo.state != null) {
                sender.addChatMessage(undoEventInfo.message);
            } else {
                // Something gone wrong with the undo implementation. This may not be tempora's fault, depending on the
                // origin of this logger.
                IChatComponent errorMsg = new ChatComponentTranslation(
                    "tempora.undo.bad_implementation",
                    loggerName,
                    ChatUtils.createHoverableClickable("[UUID]", eventID));
                errorMsg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(errorMsg);
            }
        } else {
            throw new WrongUsageException("tempora.undo.not_enabled", loggerName);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null;
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.tempora_undo.help.description");
    }

    @Override
    public String getExampleArgs() {
        return "PlayerBlockBreakLogger 5fe966ff-0603-4e44-b731-613abf66fee2";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.undo";
    }
}
