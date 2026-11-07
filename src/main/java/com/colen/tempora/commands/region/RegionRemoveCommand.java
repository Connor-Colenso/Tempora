package com.colen.tempora.commands.region;

import static com.colen.tempora.Tempora.NETWORK;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.CommandUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// /tempora region remove [name]
// Deletes a region by name, or the one the player is standing in. Regions can be nested, so if they're in more than one
// nothing gets removed and they're asked to pick by name
public class RegionRemoveCommand extends TemporaSubcommand {

    public RegionRemoveCommand() {
        super("remove", "[name]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sendUsageError(sender);
            return;
        }

        TemporaWorldRegion region;

        if (args.length == 1) {
            region = TemporaRegionRegistry.getByLabel(args[0]);
            if (region == null) {
                sender.addChatMessage(CommandUtils.regionNotFound(args[0]));
                return;
            }
        } else {
            // No name, use the region the player is standing in
            if (!(sender instanceof EntityPlayerMP player)) {
                sender.addChatMessage(CommandUtils.playerOnly());
                return;
            }

            List<TemporaWorldRegion> here = TemporaRegionRegistry
                .regionsContaining(player.dimension, player.posX, player.posY, player.posZ);

            if (here.isEmpty()) {
                sendError(sender, "tempora.command.region.remove.not_in_region");
                return;
            }

            if (here.size() > 1) {
                sendPickOne(sender, here);
                return;
            }

            region = here.get(0);
        }

        TemporaRegionRegistry.remove(region);

        // Stops it rendering for anyone looking at it
        NETWORK.sendToAll(new PacketRemoveRegionFromClient(region.getRegionUUID()));

        IChatComponent msg = new ChatComponentTranslation(
            "tempora.command.region.remove.removed",
            region.getLabel(),
            region.getMinCornerChatComponent(),
            region.getMaxCornerChatComponent(),
            region.getMidpointChatComponent());
        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

    private void sendPickOne(ICommandSender sender, List<TemporaWorldRegion> regions) {
        IChatComponent header = new ChatComponentTranslation(
            "tempora.command.region.remove.ambiguous",
            new ChatComponentNumber(regions.size()));
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        sender.addChatMessage(header);

        for (TemporaWorldRegion region : regions) {
            IChatComponent option = new ChatComponentText(" - " + region.getLabel());
            option.getChatStyle()
                .setColor(EnumChatFormatting.AQUA)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command(region.getLabel())));
            sender.addChatMessage(option);
        }
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, TemporaRegionRegistry.getAllLabels());
        }
        return null;
    }

    @Override
    public String getExampleArgs() {
        return "spawn";
    }
}
