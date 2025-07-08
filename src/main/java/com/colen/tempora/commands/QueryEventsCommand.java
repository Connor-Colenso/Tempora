package com.colen.tempora.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.TimeUtils;

import static com.colen.tempora.commands.CommandConstants.ONLY_IN_GAME;

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

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText(ONLY_IN_GAME));
            return;
        }

        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }

        int radius = parseInt(sender, args[0]);
        long seconds = TimeUtils.convertToSeconds(args[1].toLowerCase());

        String tableName = args.length == 3 ? validateFilter(args[2]) : null;

        int x = (int) Math.round(entityPlayerMP.posX);
        int y = (int) Math.round(entityPlayerMP.posY);
        int z = (int) Math.round(entityPlayerMP.posZ);

        GenericPositionalLogger.queryEventByCoordinate(sender, x, y, z, radius, seconds, tableName, entityPlayerMP.dimension);
    }

    private String validateFilter(String input) {
        for (String option : getFilterOptions()) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        throw new WrongUsageException("tempora.command.queryevents.bad_filter", input);
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
        return null; // Return null when there are no matches.
    }

    private List<String> getFilterOptions() {
        List<String> options = new ArrayList<>();
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
            options.add(logger.getSQLTableName());
        }
        return options;
    }
}
