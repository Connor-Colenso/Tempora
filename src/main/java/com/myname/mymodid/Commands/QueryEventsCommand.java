package com.myname.mymodid.Commands;

import static com.myname.mymodid.TemporaUtils.parseTime;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.myname.mymodid.Loggers.GenericLoggerPositional;

import java.util.ArrayList;
import java.util.List;

public class QueryEventsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "queryevents";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/queryevents <radius> <time>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int radius = parseInt(sender, args[0]);
        long seconds = parseTime(args[1]);
        String tableName = null;

        if (args.length == 3) {
            tableName = validateFilter(args[2]);
        }

        queryDatabases(sender, radius, seconds, tableName);
    }

    private String validateFilter(String arg) {
        if (getFilterOptions().contains(arg)) return arg;
        throw new WrongUsageException("Filter " + arg + " is invalid");
    }

    private void queryDatabases(ICommandSender sender, int radius, long seconds, String tableName) {
        if (!(sender instanceof EntityPlayerMP))  {
            sender.addChatMessage(new ChatComponentText("This command can only be run by a user in-game."));
            return;
        }

        for (String message : GenericLoggerPositional.queryEventsWithinRadiusAndTime(sender, radius, seconds, tableName)) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 3) { // Adjust the index as needed for your command's syntax.
            return getFilterOptions();
        }
        return null; // Return null or an empty list when there are no matches.
    }

    private List<String> getFilterOptions() {
        List<String> loggers = new ArrayList<>();
        for (GenericLoggerPositional loggerPositional : GenericLoggerPositional.loggerList) {
            loggers.add(loggerPositional.getTableName());
        }
        return loggers;
    }

}
