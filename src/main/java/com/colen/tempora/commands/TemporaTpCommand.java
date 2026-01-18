package com.colen.tempora.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.colen.tempora.utils.CommandUtils;

public class TemporaTpCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "tempora_tp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_tp <x> <y> <z> [dim]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP only
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return super.isUsernameIndex(args, index);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        if (args.length < 3 || args.length > 4) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        double x = parseDouble(sender, args[0]);
        double y = parseDouble(sender, args[1]);
        double z = parseDouble(sender, args[2]);

        int targetDim = player.dimension;
        if (args.length == 4) {
            targetDim = parseInt(sender, args[3]);
        }

        if (player.dimension != targetDim) {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer targetWorld = server.worldServerForDimension(targetDim);
            if (targetWorld == null) {
                throw new WrongUsageException("Invalid dimension ID"); // todo localise
            }

            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, targetDim);
        }

        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
    }
}
