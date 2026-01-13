package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.loggers.generic.GenericEventInfo.teleportChatComponent;
import static com.colen.tempora.utils.GenericUtils.getDimensionName;
import static com.colen.tempora.utils.PlayerUtils.playerNameFromUUID;
import static com.colen.tempora.utils.TimeUtils.formatTime;

import java.util.Comparator;
import java.util.List;

import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.loggers.generic.GenericEventInfo.CoordFormat;
import com.colen.tempora.networking.PacketShowRegionInWorld;
import com.colen.tempora.utils.CommandUtils;
import net.minecraft.util.IChatComponent;

/**
 * /listregions
 * Lists every stored region and provides a clickable coordinate that
 * teleports the issuer to the centre of that region.
 */
public class ListRegionsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "listregions";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    /** Usage is localised */
    @Override
    public String getCommandUsage(ICommandSender s) {
        return "/listregions [Dim ID filter]";
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

        List<RegionToRender> regions = BlockChangeRegionRegistry.getAll();

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
                    IChatComponent invalidDim = new ChatComponentTranslation("tempora.command.filtered.listregions.invalid.dimension", dimFilter);
                    invalidDim.getChatStyle().setColor(EnumChatFormatting.RED);
                    sender.addChatMessage(invalidDim);
                } else {
                    IChatComponent emptyDim = new ChatComponentTranslation("tempora.command.filtered.listregions.empty", dimensionName, dimFilter);
                    emptyDim.getChatStyle().setColor(EnumChatFormatting.RED);
                    sender.addChatMessage(emptyDim);
                }

                return;
            }
        }

        regions.sort(
            Comparator.comparingInt(RegionToRender::getDimID)
                .thenComparingLong(RegionToRender::getRenderStartTimeMs));

        if (regions.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.listregions.none"));
            return;
        }

        /* ---- list every region ---- */
        int idx = 1;
        for (RegionToRender r : regions) {

            // Centre of the region for a sensible teleport target
            double cx = (r.getMinX() + r.getMaxX()) / 2.0;
            double cy = (r.getMinY() + r.getMaxY()) / 2.0;
            double cz = (r.getMinZ() + r.getMaxZ()) / 2.0;

            /* Clickable coordinate component */
            ChatComponentTranslation tp = (ChatComponentTranslation) teleportChatComponent(
                cx,
                cy,
                cz,
                r.getDimID(),
                CoordFormat.INT);

            /* Whole entry line */
            ChatComponentTranslation line = new ChatComponentTranslation(
                "tempora.command.listregions.entry",
                idx++,
                teleportChatComponent(r.getMinX(), r.getMinY(), r.getMinZ(), r.getDimID(), CoordFormat.INT),
                teleportChatComponent(r.getMaxX(), r.getMaxY(), r.getMaxZ(), r.getDimID(), CoordFormat.INT),
                r.getDimID(),
                tp,
                formatTime(r.getRegionOriginTimeMs()),
                playerNameFromUUID(r.getPlayerAuthorUUID()));

            line.getChatStyle().setColor(EnumChatFormatting.YELLOW);
            sender.addChatMessage(line);
        }

        for (RegionToRender regionToSend : regions) {
            NETWORK.sendTo(new PacketShowRegionInWorld.RegionMsg(regionToSend), player);
        }

        if (BlockChangeLogger.isGlobalBlockChangeLoggingEnabled()) {
            IChatComponent globalLoggingMessage = new ChatComponentTranslation("tempora.command.listregions.global.logging.enabled");
            globalLoggingMessage.getChatStyle().setColor(EnumChatFormatting.DARK_RED);
            player.addChatMessage(globalLoggingMessage);
        }
    }
}
