package com.colen.tempora.commands;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.utils.CommandUtils;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;
import net.minecraft.util.IChatComponent;

import java.util.List;

import static com.colen.tempora.Tempora.NETWORK;

/**
 * /removeregion
 *
 * Deletes every stored region that currently contains the issuing player.
 * (If regions overlap, they are all removed.)
 */
public class RemoveRegionCommand extends CommandBase {

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

        // removeregion only makes sense for a player, because it users the players coords
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        List<RegionToRender> removed = BlockChangeRegionRegistry.removeRegionsContainingCoordinate(player);
        int removedCount = removed.size();


        ChatComponentTranslation msg;
        if (removedCount > 0) {
            // Plural, or not.
            String key = removedCount == 1 ? "command.tempora.removeregion.removed.single"
                : "command.tempora.removeregion.removed.plural";

            msg = new ChatComponentTranslation(key, new ChatComponentNumber(removedCount));

            for (RegionToRender region : removed) {
                // Deletes them from the players local renderer immediately.
                NETWORK.sendTo(new PacketRemoveRegionFromClient(region.getRegionUUID()), player);

                double midX = (region.getMinX()  + region.getMaxX())/2.0;
                double midY = (region.getMinY()  + region.getMaxY())/2.0;
                double midZ = (region.getMinZ()  + region.getMaxZ())/2.0;

                IChatComponent teleportComp = GenericEventInfo.teleportChatComponent(midX, midY, midZ, region.getDimID(), GenericEventInfo.CoordFormat.FLOAT_1DP);

                player.addChatMessage(new ChatComponentTranslation("tempora.region.remove.individual", region.getLabel(), teleportComp));
            }

            msg.getChatStyle()
                .setColor(EnumChatFormatting.GREEN);
            sender.addChatMessage(msg);
        } else {
            msg = new ChatComponentTranslation("command.tempora.removeregion.no_region");
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
        }
        sender.addChatMessage(msg);
    }
}
