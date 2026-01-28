package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.utils.CommandUtils;

/**
 * /listregions
 * Lists every stored region and provides a clickable coordinate that
 * teleports the issuer to the centre of that region.
 */
public class ListRegionsCommand extends TemporaCommandBase {

    public ListRegionsCommand() {
        super(
            new CommandArg("[dim_ID]", "tempora.command.list_regions.help.arg0")
        );
    }

    @Override
    public String getCommandName() {
        return "tempora_list_regions";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        List<TemporaWorldRegion> regions = BlockChangeRegionRegistry.getAll();

        Integer dimFilter;
        if (args.length == 1) {
            dimFilter = parseInt(sender, args[0]);
        } else {
            dimFilter = null;
        }

        if (dimFilter != null) {
            regions.removeIf(r -> r.getDimID() != dimFilter);
            if (regions.isEmpty()) {
                String dimensionName = getDimensionName(dimFilter);

                if (dimensionName == null) {
                    IChatComponent invalidDim = new ChatComponentTranslation(
                        "tempora.command.list_regions.filtered.invalid_dimension",
                        dimFilter);
                    invalidDim.getChatStyle()
                        .setColor(EnumChatFormatting.RED);
                    sender.addChatMessage(invalidDim);
                } else {
                    IChatComponent emptyDim = new ChatComponentTranslation(
                        "tempora.command.list_regions.filtered.no_regions",
                        dimensionName,
                        dimFilter);
                    emptyDim.getChatStyle()
                        .setColor(EnumChatFormatting.RED);
                    sender.addChatMessage(emptyDim);
                }

                return;
            }
        }

        regions.sort(
            Comparator.comparingInt(TemporaWorldRegion::getDimID)
                .thenComparingLong(TemporaWorldRegion::getRenderStartTimeMs));

        if (regions.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.list_regions.none"));
            return;
        }

        // List every region
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
            sender.addChatMessage(r.getChatComponent());
        }

        for (TemporaWorldRegion regionToSend : regions) {
            NETWORK.sendTo(new PacketShowRegionInWorld.RegionMsg(regionToSend), player);
        }

        if (BlockChangeLogger.isGlobalBlockChangeLoggingEnabled()) {
            IChatComponent globalLoggingMessage = new ChatComponentTranslation(
                "tempora.command.list_regions.global.logging.enabled");
            globalLoggingMessage.getChatStyle()
                .setColor(EnumChatFormatting.DARK_RED);
            player.addChatMessage(globalLoggingMessage);
        }
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.list_regions.help.description");
    }

    @Override
    public String getExampleArgs() {
        return "-1";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.list_regions";
    }
}
