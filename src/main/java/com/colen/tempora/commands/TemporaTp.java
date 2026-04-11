package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;

import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.utils.CommandUtils;

public class TemporaTp extends TemporaCommandBase {

    public static final String TEMPORA_TP = "tempora_tp";

    @Override
    public String getCommandName() {
        return TEMPORA_TP;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
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

    @Override
    public String getExampleCommand() {
        return "400 42 -123 1";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.explode.tempora_tp.description");
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.teleport";
    }
}
