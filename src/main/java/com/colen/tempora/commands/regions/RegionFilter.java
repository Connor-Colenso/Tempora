package com.colen.tempora.commands.regions;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.loggers.block_change.region_registry.TemporaRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.CommandUtils;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

public class RegionFilter extends TemporaCommandBase {

    // /tempora_region_filter whitelist <LoggerName> [Region Label]

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

        // tempora_region_filter only makes sense for a player because it users the players coords
        if (!(sender instanceof EntityPlayer player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        // Syntax check
        if (args.length >= 4) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // Whitelist / Blacklist check
        boolean isWhitelist;

        if (args[0].equalsIgnoreCase("whitelist")) {
            isWhitelist = true;
        } else if (args[0].equalsIgnoreCase("blacklist")) {
            isWhitelist = false;
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // Logger filter check
        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(args[1]);
        if (logger == null) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // Filter regions by label
        List<TemporaWorldRegion> allRegions = TemporaRegionRegistry.getAll();
        List<TemporaWorldRegion> filteredRegions = new ArrayList<>();

        if (args.length == 3) {
            String labelFilter = args[2];
            for (TemporaWorldRegion region : allRegions) {
                if (region.getLabel().equalsIgnoreCase(labelFilter)) {
                    filteredRegions.add(region);
                }
            }
        } else {
            // Filter regions by player position.
            filteredRegions = TemporaRegionRegistry.removeRegionsIntersectingPlayer(player);
        }

        for (TemporaWorldRegion region : filteredRegions) {
            if (isWhitelist) {
                region.addWhitelistedLogger(logger);
            } else {
                region.addBlacklistedLogger(logger);
            }
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
