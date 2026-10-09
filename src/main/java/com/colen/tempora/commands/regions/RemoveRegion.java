package com.colen.tempora.commands.regions;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.CommandUtils;

/**
 * Deletes a region by label, or the region the player is standing in. Regions can be nested, so if the player is
 * inside more than one, nothing is removed and they are asked to pick one by label instead.
 */
public class RemoveRegion extends TemporaCommandBase {

    public RemoveRegion() {
        super(new CommandArg("[label]", "tempora.command.remove_region.help.arg0"));
    }

    @Override
    public String getCommandName() {
        return "tempora_remove_region";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
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
            // Without a label, use the region the player is standing in.
            if (!(sender instanceof EntityPlayerMP player)) {
                sender.addChatMessage(CommandUtils.playerOnly());
                return;
            }

            List<TemporaWorldRegion> here = TemporaRegionRegistry
                .regionsContaining(player.dimension, player.posX, player.posY, player.posZ);

            if (here.isEmpty()) {
                IChatComponent msg = new ChatComponentTranslation("tempora.command.remove_region.no_region");
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }

            if (here.size() > 1) {
                sendPickOne(sender, here);
                return;
            }

            region = here.get(0);
        }

        TemporaRegionRegistry.remove(region);

        // Stops it rendering for anyone currently looking at it.
        NETWORK.sendToAll(new PacketRemoveRegionFromClient(region.getRegionUUID()));

        IChatComponent msg = new ChatComponentTranslation(
            "tempora.command.remove_region.removed",
            region.getLabel(),
            region.getMinCornerChatComponent(),
            region.getMaxCornerChatComponent(),
            region.getMidpointChatComponent());
        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

    private void sendPickOne(ICommandSender sender, List<TemporaWorldRegion> regions) {
        IChatComponent header = new ChatComponentTranslation("tempora.command.remove_region.ambiguous", regions.size());
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        sender.addChatMessage(header);

        for (TemporaWorldRegion region : regions) {
            IChatComponent option = new ChatComponentText(" - " + region.getLabel());
            option.getChatStyle()
                .setColor(EnumChatFormatting.AQUA)
                .setChatClickEvent(
                    new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        "/" + getCommandName() + " " + region.getLabel()));
            sender.addChatMessage(option);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsFromIterableMatchingLastWord(args, TemporaRegionRegistry.getAllLabels());
        }
        return null;
    }

    @Override
    public String getExampleCommand() {
        return "/" + getCommandName() + " spawn";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.remove_region.help.description");
    }
}
