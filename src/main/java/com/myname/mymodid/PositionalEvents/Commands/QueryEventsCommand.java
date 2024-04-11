package com.myname.mymodid.PositionalEvents.Commands;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.modularui.common.internal.network.NetworkHandler;
import com.myname.mymodid.PositionalEvents.Loggers.BlockBreak.BlockBreakQueueElement;
import com.myname.mymodid.PositionalEvents.Loggers.Command.CommandQueueElement;
import com.myname.mymodid.PositionalEvents.Loggers.GenericPacket;
import com.myname.mymodid.PositionalEvents.Loggers.ISerializable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.myname.mymodid.TemporaUtils;

import static com.myname.mymodid.Tempora.NETWORK;

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
            {
                BlockBreakQueueElement q1 = new BlockBreakQueueElement(1,2,3,4);
                BlockBreakQueueElement q2 = new BlockBreakQueueElement(5,6,7,8);
                q1.playerUUIDWhoBrokeBlock = "TEST1";
                q2.playerUUIDWhoBrokeBlock = "TEST2";
                q1.blockID = 7;
                q1.metadata = 0;
                q2.blockID = 3;
                q2.metadata = 2;

                ArrayList<ISerializable> test = new ArrayList<>();
                test.add(q1);
                test.add(q2);

                GenericPacket packet = new GenericPacket(test);

                NETWORK.sendTo(packet, (EntityPlayerMP) sender);
            }

            {
                CommandQueueElement q1 = new CommandQueueElement();
                CommandQueueElement q2 = new CommandQueueElement();
                q1.playerUUIDWhoIssuedCommand = "name1";
                q2.playerUUIDWhoIssuedCommand = "name2";
                q1.commandName = "cmd1";
                q2.commandName = "cmd2";
                q1.arguments = "arg1, arg2";
                q2.arguments = "arg3, arg4";

                ArrayList<ISerializable> test = new ArrayList<>();
                test.add(q1);
                test.add(q2);

                GenericPacket packet = new GenericPacket(test);

                NETWORK.sendTo(packet, (EntityPlayerMP) sender);
            }

            return;
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

        GenericPositionalLogger.queryEventsWithinRadiusAndTime(sender, radius, seconds, tableName);
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
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.loggerList) {
            options.add(logger.getTableName());
        }
        return options;
    }
}
