package com.colen.tempora.commands.regions;

import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.loggers.block_change.region_registry.TemporaRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.utils.CommandUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.List;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.OP_ONLY;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

public class RegionFilter extends TemporaCommandBase {

    // /tempora_region_filter whitelist [LoggerName]

    @Override
    public String getCommandName() {
        return "tempora_region_filter";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        // Syntax check
        if (args.length > 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // tempora_region_filter only makes sense for a player because it users the players coords
        if (!(sender instanceof EntityPlayer player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        int playerX = (int) Math.round(player.posX);
        int playerY = (int) Math.round(player.posY);
        int playerZ = (int) Math.round(player.posZ);

        List<TemporaWorldRegion> allRegions = TemporaRegionRegistry.getAll();

        for (TemporaWorldRegion region : allRegions) {

        }
    }

    @Override
    public String getExampleCommand() {
        return "";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.tempora_region_filter.description");
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.tempora_region_filter";
    }
}
