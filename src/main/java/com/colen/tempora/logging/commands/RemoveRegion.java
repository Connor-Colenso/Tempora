package com.colen.tempora.logging.commands;

import java.util.Arrays;
import java.util.List;

import com.colen.tempora.logging.loggers.block_change.RegionRegistry;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

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
    public List<String> getCommandAliases() {
        return Arrays.asList("delregion", "rmregion");
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
        if (args.length != 0)
            throw new WrongUsageException(getCommandUsage(sender));

        // Only players have a sensible position
        if (!(sender instanceof EntityPlayer))
            throw new CommandException("Only a player can run /removeregion.");

        ChunkCoordinates pos = sender.getPlayerCoordinates();
        World  world = sender.getEntityWorld();
        int dim = world.provider.dimensionId;

        int removed = RegionRegistry.get(world)
            .removeRegionsContainingBlock(dim, pos.posX, pos.posY, pos.posZ);

        if (removed > 0) {
            sender.addChatMessage(new ChatComponentText(
                String.format("§aRemoved %d region%s at your position.",
                    removed, removed == 1 ? "" : "s")));
        } else {
            sender.addChatMessage(new ChatComponentText("§eNo region found at your position."));
        }
    }
}
