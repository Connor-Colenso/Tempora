package com.colen.tempora.logging.commands;

import java.util.Arrays;
import java.util.List;

import com.colen.tempora.logging.loggers.block_change.IntRegion;
import com.colen.tempora.logging.loggers.block_change.RegionRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * /createregion <x1> <y1> <z1> <x2> <y2> <z2>
 * Creates an axis‑aligned, integer‑bounded region in the sender’s current dimension
 * and stores it in RegionRegistry.
 */
public class CreateRegion extends CommandBase {

    @Override
    public String getCommandName() {
        return "createregion";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/createregion <x1> <y1> <z1> <x2> <y2> <z2>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;  // OP‑only by default
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 6)
            throw new WrongUsageException(getCommandUsage(sender));

        /* ---- parse six integers ---- */
        int x1 = parseInt(sender, args[0]);
        int y1 = parseInt(sender, args[1]);
        int z1 = parseInt(sender, args[2]);
        int x2 = parseInt(sender, args[3]);
        int y2 = parseInt(sender, args[4]);
        int z2 = parseInt(sender, args[5]);

        /* ---- build & store region ---- */
        World world = sender.getEntityWorld();
        int dim    = world.provider.dimensionId;

        IntRegion region = new IntRegion(dim, x1, y1, z1, x2, y2, z2);
        RegionRegistry.get(world).add(region);

        sender.addChatMessage(new ChatComponentText(
            String.format("§aRegion created: (%d,%d,%d) → (%d,%d,%d) in dimension %d.",
                x1, y1, z1, x2, y2, z2, dim)));
    }

}
