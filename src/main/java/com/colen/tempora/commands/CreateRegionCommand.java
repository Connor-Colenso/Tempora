package com.colen.tempora.commands;

import static com.colen.tempora.loggers.generic.GenericEventInfo.teleportChatComponent;

import java.util.UUID;

import com.colen.tempora.rendering.regions.RegionRenderMode;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.utils.CommandUtils;

/**
 * /createregion <x1> <y1> <z1> <x2> <y2> <z2>
 * Creates an axis‑aligned, integer‑bounded region in the sender’s current dimension
 * and stores it in RegionRegistry.
 */
public class CreateRegionCommand extends CommandBase {

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
        return 2; // OP‑only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 6) {
            CommandUtils.wrongUsage(getCommandUsage(sender));
            return;
        }

        // Parse six integers for xyz start & xyz end.
        int[] coords = new int[6];

        for (int i = 0; i < 6; i++) {
            try {
                coords[i] = parseInt(sender, args[i]);
            } catch (NumberFormatException e) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.create.region.non.numeric.coordinate",
                    args[i]);

                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
        }

        // Build and store region
        World world = sender.getEntityWorld();
        int dim = world.provider.dimensionId;

        RegionToRender region = new RegionToRender(
            dim,
            coords[0],
            coords[1],
            coords[2],
            coords[3],
            coords[4],
            coords[5],
            System.currentTimeMillis(),
            UUID.randomUUID()
                .toString(),
            RegionRenderMode.BLOCK_CHANGE);
        BlockChangeRegionRegistry.add(region);

        ChatComponentTranslation msg = new ChatComponentTranslation(
            "command.tempora.region.created",
            teleportChatComponent(coords[0], coords[1], coords[2], dim, GenericEventInfo.CoordFormat.INT),
            teleportChatComponent(coords[3], coords[4], coords[5], dim, GenericEventInfo.CoordFormat.INT),
            dim); // dimension ID

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

}
