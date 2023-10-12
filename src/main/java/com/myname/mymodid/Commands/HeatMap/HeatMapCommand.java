package com.myname.mymodid.Commands.HeatMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import com.myname.mymodid.TemporaUtils;

public class HeatMapCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "heatmap";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/heatmap <name> <maxtime> <should auto update (T/F)>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String playerToBeTracked = args[0];
        sender.addChatMessage(new ChatComponentText("Now tracking player " + playerToBeTracked + " with heatmap."));

        long maxTime = TemporaUtils.parseTime(args[1]);

        if ((args.length == 3) && (args[2].equalsIgnoreCase("t"))) {
            sender.addChatMessage(new ChatComponentText("Auto-updating heatmap enabled."));
            HeatMapUpdater.addTracking(sender.getCommandSenderName(), maxTime, playerToBeTracked);
        }

        HeatMapUtil.queryAndSendDataToPlayer(sender, maxTime, playerToBeTracked);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }
}
