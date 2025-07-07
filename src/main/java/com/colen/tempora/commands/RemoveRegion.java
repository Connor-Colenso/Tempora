package com.colen.tempora.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.colen.tempora.logging.loggers.block_change.RegionRegistry;

/**
 * /removeregion
 *
 * Deletes every stored region that currently contains the issuing player.
 * (If regions overlap, they are all removed.)
 */
public class RemoveRegion extends CommandBase {

    @Override
    public String getCommandName() {
        return "removeregion";
    }


    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/removeregion";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP‑only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        // Syntax check
        if (args.length != 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // removeregion only makes sense for a player, because it users a players coords
        if (!(sender instanceof EntityPlayer)) {
            // This could go to an unlocalised client, like a terminal, so don't localise.
            throw new CommandException("Only players in world may use this command.");
        }

        ChunkCoordinates pos = sender.getPlayerCoordinates();
        World              world = sender.getEntityWorld();
        int                dim = world.provider.dimensionId;

        int removed = RegionRegistry.get(world)
            .removeRegionsContainingBlock(dim, pos.posX, pos.posY, pos.posZ);

        ChatComponentTranslation msg;
        if (removed > 0) {
            // Plural, or not.
            String key = removed == 1
                ? "command.tempora.removeregion.removed.single"
                : "command.tempora.removeregion.removed.plural";

            msg = new ChatComponentTranslation(key, removed);
            msg.getChatStyle().setColor(EnumChatFormatting.GREEN);
            sender.addChatMessage(msg);
        } else {
            msg = new ChatComponentTranslation("command.tempora.removeregion.no_region");
            msg.getChatStyle().setColor(EnumChatFormatting.RED);
        }
        sender.addChatMessage(msg);
    }
}
