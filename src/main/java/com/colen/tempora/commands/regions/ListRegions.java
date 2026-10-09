package com.colen.tempora.commands.regions;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.OP_ONLY;
import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.utils.CommandUtils;

/**
 * /tempora_list_regions [dim_ID]
 * Lists every stored region with its rules, and clickable coordinates that teleport the issuer there.
 * Players also get the regions drawn in the world for a short time.
 */
public class ListRegions extends TemporaCommandBase {

    public ListRegions() {
        super(new CommandArg("[dim_ID]", "tempora.command.list_regions.help.arg0"));
    }

    @Override
    public String getCommandName() {
        return "tempora_list_regions";
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

        List<TemporaWorldRegion> regions = TemporaRegionRegistry.getAll();

        if (args.length == 1) {
            int dimFilter = parseInt(sender, args[0]);

            if (!DimensionManager.isDimensionRegistered(dimFilter)) {
                IChatComponent invalidDim = new ChatComponentTranslation("tempora.dimension.invalid", dimFilter);
                invalidDim.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(invalidDim);
                return;
            }

            regions.removeIf(r -> r.getDimID() != dimFilter);
            if (regions.isEmpty()) {
                IChatComponent emptyDim = new ChatComponentTranslation(
                    "tempora.command.list_regions.filtered.no_regions",
                    getDimensionName(dimFilter),
                    dimFilter);
                emptyDim.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(emptyDim);
                return;
            }
        }

        if (regions.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.list_regions.none"));
            return;
        }

        regions.sort(
            Comparator.comparingInt(TemporaWorldRegion::getDimID)
                .thenComparingLong(TemporaWorldRegion::getRegionOriginTimeMs));

        // List every region, grouped by dimension.
        HashSet<Integer> dimIDsSeen = new HashSet<>();
        for (TemporaWorldRegion r : regions) {
            if (dimIDsSeen.add(r.getDimID())) {
                IChatComponent dimBanner = new ChatComponentTranslation(
                    "tempora.region.dimension_banner",
                    getDimensionName(r.getDimID()),
                    r.getDimID());
                dimBanner.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(dimBanner);
            }
            sender.addChatMessage(r.getTeleportToRegionChatComponent());
            sender.addChatMessage(
                new ChatComponentText("  ").appendSibling(r.getRulesChatComponent()));
        }

        if (sender instanceof EntityPlayerMP player) {
            for (TemporaWorldRegion regionToSend : regions) {
                NETWORK.sendTo(new PacketShowRegionInWorld.RegionMsg(regionToSend), player);
            }
        }
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.list_regions.help.description");
    }

    @Override
    public String getExampleCommand() {
        return "/" + getCommandName() + " -1";
    }

}
