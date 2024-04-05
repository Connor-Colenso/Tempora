package com.myname.mymodid.Commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.myname.mymodid.Loggers.GenericLoggerPositional;
import com.myname.mymodid.TemporaUtils;

public class QueryEventsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "queryevents";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/queryevents <radius> <time> [filter]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int radius = parseInt(sender, args[0]);
        long seconds = TemporaUtils.parseTime(args[1]);
        String tableName = args.length == 3 ? validateFilter(args[2]) : null;

        queryDatabases(sender, radius, seconds, tableName);
    }

    private String validateFilter(String input) {
        for (String option : getFilterOptions()) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        throw new WrongUsageException("Filter " + input + " is invalid");
    }

    private void queryDatabases(ICommandSender sender, int radius, long seconds, String tableName) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("This command can only be run by a user in-game."));
            return;
        }

        List<String> messages = GenericLoggerPositional
            .queryEventsWithinRadiusAndTime(sender, radius, seconds, tableName);
        for (String message : messages) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            String partialFilter = args[2].toLowerCase();
            List<String> matchingOptions = new ArrayList<>();
            for (String option : getFilterOptions()) {
                if (option.toLowerCase()
                    .startsWith(partialFilter)) {
                    matchingOptions.add(option);
                }
            }
            return matchingOptions;
        }
        return null; // Return null or an empty list when there are no matches.
    }

    private List<String> getFilterOptions() {
        List<String> options = new ArrayList<>();
        for (GenericLoggerPositional logger : GenericLoggerPositional.loggerList) {
            options.add(logger.getTableName());
        }
        return options;
    }
}
