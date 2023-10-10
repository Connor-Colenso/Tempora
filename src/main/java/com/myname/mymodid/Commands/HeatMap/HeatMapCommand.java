package com.myname.mymodid.Commands.HeatMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

public class HeatMapCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "heatmap";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/heatmap <name>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String playerName = args[0];
        sender.addChatMessage(new ChatComponentText("Now tracking player " + playerName + " with heatmap. Run command again to stop tracking."));
        HeatMapUtil.queryAndSendDataToPlayer(sender, playerName);
        TrackPlayerUpdater.addTracking(sender.getCommandSenderName(), playerName);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }
}
