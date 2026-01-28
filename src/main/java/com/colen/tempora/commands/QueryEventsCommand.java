package com.colen.tempora.commands;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
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

        if (args.length < 3) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }

        int radius = parseInt(sender, args[0]);
        long seconds = TimeUtils.convertToSeconds(args[1].toLowerCase());

        if (radius < 0) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.range.negative"));
            return;
        }

        String tableName = args.length == 3 ? validateLoggerName(args[2]) : null;
        if (tableName == null) return;

        int x = (int) Math.round(entityPlayerMP.posX);
        int y = (int) Math.round(entityPlayerMP.posY);
        int z = (int) Math.round(entityPlayerMP.posZ);

        TemporaLoggerManager.getLogger(tableName)
            .getDatabaseManager()
            .queryEventByCoordinate(sender, x, y, z, radius, seconds, entityPlayerMP.dimension);
    }

    private String validateLoggerName(String input) {
        for (String option : TemporaLoggerManager.getAllLoggerNames()) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        throw new WrongUsageException("tempora.command.query_events.bad_filter", input);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
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
