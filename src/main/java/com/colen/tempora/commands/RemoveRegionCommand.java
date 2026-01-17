package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.utils.CommandUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

/**
 * /removeregion
 * Deletes every stored region that currently contains the issuing player.
 * (If regions overlap, they are all removed.)
 */
public class RemoveRegionCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "remove_region";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/remove_region";
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

        List<TemporaWorldRegion> removed = BlockChangeRegionRegistry.removeRegionsIntersectingPlayer(player);
        int removedCount = removed.size();

        ChatComponentTranslation msg;
        if (removedCount > 0) {
            // Plural, or not.
            String key = removedCount == 1 ? "tempora.command.remove_region.removed.single"
                : "tempora.command.remove_region.removed.plural";

            msg = new ChatComponentTranslation(key, new ChatComponentNumber(removedCount));

            for (TemporaWorldRegion region : removed) {
                // Deletes them from the players local renderer immediately.
                NETWORK.sendTo(new PacketRemoveRegionFromClient(region.getRegionUUID()), player);

                IChatComponent removedMessageOfRegion = new ChatComponentTranslation(
                    "tempora.region.remove.individual",
                    region.getLabel(),
                    teleportChatComponent(region.getMinX(), region.getMinY(), region.getMinZ(), region.getDimID()),
                    teleportChatComponent(region.getMaxX(), region.getMaxY(), region.getMaxZ(), region.getDimID()),
                    teleportChatComponent(region.getMidX(), region.getMidY(), region.getMidZ(), region.getDimID()));

                removedMessageOfRegion.getChatStyle()
                    .setColor(EnumChatFormatting.GREEN);

                player.addChatMessage(removedMessageOfRegion);
            }

            msg.getChatStyle()
                .setColor(EnumChatFormatting.GREEN);
            sender.addChatMessage(msg);
        } else {
            msg = new ChatComponentTranslation("tempora.command.remove_region.no_region");
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
        }
        sender.addChatMessage(msg);
    }
}
