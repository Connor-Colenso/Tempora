package com.colen.tempora.commands;

import java.util.List;

import com.colen.tempora.loggers.generic.GenericQueueElement.CoordFormat;
import com.colen.tempora.networking.PacketRegionSync;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.loggers.block_change.IntRegion;
import com.colen.tempora.loggers.block_change.RegionRegistry;

import static com.colen.tempora.commands.CommandConstants.ONLY_IN_GAME;
import static com.colen.tempora.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

/**
 * /listregions
 * Lists every stored region and provides a clickable coordinate that
 * teleports the issuer to the centre of that region.
 */
public class ListRegionsCommand extends CommandBase {

    @Override public String getCommandName()             { return "listregions"; }
    @Override public int    getRequiredPermissionLevel() { return 2; }

    /** Usage is localised */
    @Override public String getCommandUsage(ICommandSender s) {
        return "/listregions";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (args.length > 2)
            throw new WrongUsageException(getCommandUsage(sender));

        Integer dimFilter;
        if (args.length == 2) {
            dimFilter = parseInt(sender, args[1]);
        } else {
            dimFilter = null;
        }

        if (!(sender instanceof EntityPlayerMP player))
            throw new CommandException(ONLY_IN_GAME);

        String playerName = player.getCommandSenderName();

        List<IntRegion> regions = RegionRegistry.getAll();

        if (dimFilter != null) {
            regions.removeIf(r -> r.dim != dimFilter);
        }

        if (regions.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.listregions.none"));
            return;
        }

        /* ---- list every region ---- */
        int idx = 1;
        for (IntRegion r : regions) {

            // Centre of the region for a sensible teleport target
            double cx = (r.minX + r.maxX) / 2.0;
            double cy = (r.minY + r.maxY) / 2.0;
            double cz = (r.minZ + r.maxZ) / 2.0;

            /* Clickable coordinate component */
            ChatComponentTranslation tp =
                (ChatComponentTranslation) generateTeleportChatComponent(
                    cx, cy, cz, r.dim, playerName,
                    CoordFormat.INT);

            /* Whole entry line */
            ChatComponentTranslation line =
                new ChatComponentTranslation(
                    "tempora.command.listregions.entry",
                    idx++,
                    generateTeleportChatComponent(r.minX, r.minY, r.minZ, r.dim, playerName, CoordFormat.INT),
                    generateTeleportChatComponent(r.maxX, r.maxY, r.maxZ, r.dim, playerName, CoordFormat.INT),
                    r.dim,
                    tp);

            line.getChatStyle().setColor(EnumChatFormatting.YELLOW);
            sender.addChatMessage(line);
        }

        PacketRegionSync.send(player, regions);
    }
}
