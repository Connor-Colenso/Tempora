package com.colen.tempora.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.colen.tempora.commands.command_base.TemporaSubcommand;

/**
 * /tempora tp <x> <y> <z> [dim_ID]
 * Teleports across dimensions. Hidden, as it is run by clicking coordinates in Tempora's chat output.
 */
public class TeleportCommand extends TemporaSubcommand {

    public TeleportCommand() {
        super("tp", "<x> <y> <z>", "[dim_ID]");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length < 3 || args.length > 4) {
            sendUsageError(sender);
            return;
        }

        double x = CommandBase.parseDouble(sender, args[0]);
        double y = CommandBase.parseDouble(sender, args[1]);
        double z = CommandBase.parseDouble(sender, args[2]);
        int targetDim = args.length == 4 ? CommandBase.parseInt(sender, args[3]) : player.dimension;

        if (player.dimension != targetDim) {
            if (MinecraftServer.getServer()
                .worldServerForDimension(targetDim) == null) {
                sendError(sender, "tempora.dimension.invalid", targetDim);
                return;
            }

            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, targetDim);
        }

        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
    }

    @Override
    public String getExampleArgs() {
        return "400 42 -123 1";
    }
}
