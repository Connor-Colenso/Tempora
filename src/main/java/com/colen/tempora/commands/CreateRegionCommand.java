package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.colen.tempora.utils.TemporaCommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.colen.tempora.utils.CommandUtils;

/**
 * Creates an axis‑aligned, integer‑bounded region in the sender’s current dimension
 * and stores it in RegionRegistry.
 */
public class CreateRegionCommand extends TemporaCommandBase {

    @Override
    public String getCommandName() {
        return "tempora_create_region";
    }


    @Override
    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.create_region.help.description");
    }

    @Override
    public IChatComponent getCommandExample() {
        return new ChatComponentTranslation("tempora.command.create_region.help.example");
    }

    @Override
    public List<IChatComponent> getArgsDescriptions() {
        ArrayList<IChatComponent> argsDescriptions = new ArrayList<>();

        argsDescriptions.add(new ChatComponentTranslation("tempora.command.create_region.help.arg1"));
        argsDescriptions.add(new ChatComponentTranslation("tempora.command.create_region.help.arg2"));
        argsDescriptions.add(new ChatComponentTranslation("tempora.command.create_region.help.arg3"));
        argsDescriptions.add(new ChatComponentTranslation("tempora.command.create_region.help.arg4"));

        return argsDescriptions;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "§a/tempora_create_region §6<label> §c<x1> <y1> <z1> §5<x2> <y2> <z2> §d[dim ID]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP‑only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // label + 6 coords + optional dim
        if (args.length != 7 && args.length != 8) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        final String label = args[0];

        // Parse xyz start & xyz end
        int[] coords = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                coords[i] = parseInt(sender, args[i + 1]);
            } catch (NumberFormatException e) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.create_region.non_numeric_coordinate",
                    args[i + 1]);
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
        }

        // Dimension: optional argument
        int dim;
        if (args.length == 8) {
            try {
                dim = parseInt(sender, args[7]);
            } catch (NumberFormatException e) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.create_region.non.numeric.dimension",
                    args[7]);
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
        } else {
            dim = sender.getEntityWorld().provider.dimensionId;
        }

        // Build region
        TemporaWorldRegion region = new TemporaWorldRegion(
            dim,
            coords[0],
            coords[1],
            coords[2],
            coords[3],
            coords[4],
            coords[5]);

        region.setLabel(label);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setRenderMode(RegionRenderMode.BLOCK_CHANGE);
        region.setRegionOriginTimeMs(System.currentTimeMillis());

        if (sender instanceof EntityPlayerMP player) {
            region.setPlayerAuthorUUID(
                player.getUniqueID()
                    .toString());
        } else {
            region.setPlayerAuthorUUID(UNKNOWN_PLAYER_NAME);
        }

        BlockChangeRegionRegistry.add(region);

        // Feedback
        ChatComponentTranslation msg = new ChatComponentTranslation(
            "tempora.command.create_region.success",
            label,
            teleportChatComponent(region.getMinX(), region.getMinY(), region.getMinZ(), dim),
            teleportChatComponent(region.getMaxX(), region.getMaxY(), region.getMaxZ(), dim),
            teleportChatComponent(region.getMidX(), region.getMidY(), region.getMidZ(), dim));

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

}
