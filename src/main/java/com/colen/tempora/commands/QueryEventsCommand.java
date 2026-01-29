package com.colen.tempora.commands;

import java.util.List;

import com.colen.tempora.commands.command_base.ArgParser;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TimeUtils;

public class QueryEventsCommand extends TemporaCommandBase {

    public QueryEventsCommand() {
        super(
            new CommandArg("<event_filter>", "tempora.command.query_events.help.arg0"),
            new CommandArg("<radius>", "tempora.command.query_events.help.arg1"),
            new CommandArg("<since>", "tempora.command.query_events.help.arg2"),
            new CommandArg("[until]", "tempora.command.query_events.help.arg3"));
    }

    @Override
    public String getCommandName() {
        return "tempora_query_events";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        ArgParser parser = new ArgParser(args, entityPlayerMP);

        if (! parser.minArgs(3)) {
            entityPlayerMP.addChatMessage(CommandUtils.tooFewArgs(3));
            entityPlayerMP.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        int radius;
        long seconds;
        String tableName;

        try {
            // Parse radius
            IChatComponent radiusError = new ChatComponentTranslation("tempora.command.error.radius");
            radius = parser.getPositiveInteger(0, radiusError);

            // Parse time in seconds (convert from string)
            IChatComponent secondsError = new ChatComponentTranslation("tempora.command.error.seconds");
            String timeArg = parser.getString(1, secondsError);
            seconds = TimeUtils.convertToSeconds(timeArg);

            // Validate table name
            IChatComponent tableNameError = new ChatComponentTranslation("tempora.command.query_events.bad_filter", args[2]);
            tableName = parser.getTableName(2, tableNameError);

        } catch (CommandException e) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        if (radius < 0) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.range.negative"));
            return;
        }

        int x = (int) Math.round(entityPlayerMP.posX);
        int y = (int) Math.round(entityPlayerMP.posY);
        int z = (int) Math.round(entityPlayerMP.posZ);

        TemporaLoggerManager.getLogger(tableName)
            .getDatabaseManager()
            .queryEventByCoordinate(sender, x, y, z, radius, seconds, entityPlayerMP.dimension);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null; // Return null when there are no matches.
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.query_events.help.description");
    }

    @Override
    public String getExampleArgs() {
        return "PlayerBlockBreakLogger 25 1day 40min";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.query_events";
    }
}
