package com.colen.tempora.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.colen.tempora.loggers.block_change.BlockChangeRecordingRegion;
import com.colen.tempora.loggers.block_change.RegionRegistry;

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
        return 2; // OP‑only by default
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 6) throw new WrongUsageException(getCommandUsage(sender));

        /* ---- parse six integers ---- */
        int x1 = parseInt(sender, args[0]);
        int y1 = parseInt(sender, args[1]);
        int z1 = parseInt(sender, args[2]);
        int x2 = parseInt(sender, args[3]);
        int y2 = parseInt(sender, args[4]);
        int z2 = parseInt(sender, args[5]);

        /* ---- build & store region ---- */
        World world = sender.getEntityWorld();
        int dim = world.provider.dimensionId;

        BlockChangeRecordingRegion region = new BlockChangeRecordingRegion(
            dim,
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            System.currentTimeMillis());
        RegionRegistry.add(region);

        ChatComponentTranslation msg = new ChatComponentTranslation(
            "command.tempora.region.created",
            x1,
            y1,
            z1, // first corner
            x2,
            y2,
            z2, // second corner
            dim); // dimension ID

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

}
