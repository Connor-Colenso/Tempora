package com.colen.tempora.commands.region;

import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// /tempora region list [dim_ID]
// Lists every region with its rules and clickable coords to teleport there. Players also get them drawn in the world
// for a bit
public class RegionListCommand extends TemporaSubcommand {

    public RegionListCommand() {
        super("list", "[dim_ID]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sendUsageError(sender);
            return;
        }

        List<TemporaWorldRegion> regions = TemporaRegionRegistry.getAll();

        if (args.length == 1) {
            int dimFilter = CommandBase.parseInt(sender, args[0]);

            if (!DimensionManager.isDimensionRegistered(dimFilter)) {
                sendError(sender, "tempora.dimension.invalid", new ChatComponentNumber(dimFilter));
                return;
            }

            regions.removeIf(r -> r.getDimID() != dimFilter);
            if (regions.isEmpty()) {
                sendError(
                    sender,
                    "tempora.command.region.list.no_regions_in_dim",
                    getDimensionName(dimFilter),
                    new ChatComponentNumber(dimFilter));
                return;
            }
        }

        if (regions.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.region.list.none"));
            return;
        }

        regions.sort(
            Comparator.comparingInt(TemporaWorldRegion::getDimID)
                .thenComparingLong(TemporaWorldRegion::getRegionOriginTimeMs));

        // Every region, grouped by dimension
        HashSet<Integer> dimIDsSeen = new HashSet<>();
        for (TemporaWorldRegion r : regions) {
            if (dimIDsSeen.add(r.getDimID())) {
                IChatComponent dimBanner = new ChatComponentTranslation(
                    "tempora.region.dimension_banner",
                    getDimensionName(r.getDimID()),
                    new ChatComponentNumber(r.getDimID()));
                dimBanner.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(dimBanner);
            }
            sender.addChatMessage(r.getTeleportToRegionChatComponent());
            sender.addChatMessage(new ChatComponentText("  ").appendSibling(r.getRulesChatComponent()));
        }

        if (sender instanceof EntityPlayerMP player) {
            for (TemporaWorldRegion regionToSend : regions) {
                PlayerPackets.sendTo(new PacketShowRegionInWorld.RegionMsg(regionToSend), player);
            }
        }
    }

    @Override
    public String getExampleArgs() {
        return "-1";
    }
}
