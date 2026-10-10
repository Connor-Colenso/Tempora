package com.colen.tempora.commands.region;

import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.CommandUtils;

/**
 * /tempora region check [logger]
 * Shows, for each positional logger, whether it logs at the player's position and what decided that. Global loggers
 * have no position, so regions never affect them.
 */
public class RegionCheckCommand extends TemporaSubcommand {

    public RegionCheckCommand() {
        super("check", "[logger]");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length > 1) {
            sendUsageError(sender);
            return;
        }

        List<GenericPositionalLogger<?>> loggers = new ArrayList<>();
        if (args.length == 1) {
            GenericLogger<?> logger = TemporaLoggerManager.findLogger(args[0]);
            if (logger instanceof GenericGlobalLogger) {
                sendError(sender, "tempora.command.region.global_logger", logger.getShortName());
                return;
            }
            if (!(logger instanceof GenericPositionalLogger<?>positional)) {
                LookupCommand.sendUnknownLogger(sender, args[0], TemporaLoggerManager.getPositionalShortNames());
                return;
            }
            loggers.add(positional);
        } else {
            loggers.addAll(TemporaLoggerManager.getPositionalLoggers());
            loggers.sort(Comparator.comparing(GenericPositionalLogger::getShortName));
        }

        int dimID = player.dimension;
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;

        IChatComponent header = new ChatComponentTranslation(
            "tempora.command.region.check.header",
            CommandUtils.teleportChatComponent(x, y, z, dimID, CommandUtils.TeleportType.EXACT),
            getDimensionName(dimID),
            dimID);
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        sender.addChatMessage(header);

        for (GenericPositionalLogger<?> logger : loggers) {
            sender.addChatMessage(explain(logger, dimID, x, y, z));
        }
    }

    // Mirrors GenericPositionalLogger.canLogHere, but keeps track of why.
    private static IChatComponent explain(GenericPositionalLogger<?> logger, int dimID, double x, double y, double z) {
        String loggerName = logger.getLoggerName();

        LogRule result;
        IChatComponent reason;

        if (!logger.isEnabledByConfig()) {
            result = LogRule.OFF;
            reason = new ChatComponentTranslation("tempora.command.region.check.reason.disabled");
        } else {
            TemporaWorldRegion deciding = TemporaRegionRegistry.findDecidingRegion(loggerName, dimID, x, y, z);

            if (deciding == null) {
                result = logger.logsByDefault() ? LogRule.ON : LogRule.OFF;
                reason = new ChatComponentTranslation("tempora.command.region.check.reason.default");
            } else {
                result = deciding.getRule(loggerName);
                TemporaWorldRegion tiedWith = findTie(deciding, loggerName, dimID, x, y, z);

                reason = tiedWith == null
                    ? new ChatComponentTranslation("tempora.command.region.check.reason.region", deciding.getLabel())
                    : new ChatComponentTranslation(
                        "tempora.command.region.check.reason.tie",
                        deciding.getLabel(),
                        tiedWith.getLabel());
            }
        }

        reason.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        return new ChatComponentTranslation(
            "tempora.command.region.check.entry",
            logger.getShortName(),
            TemporaWorldRegion.ruleChatComponent(result),
            reason);
    }

    // An equally sized region here with the opposite rule, which the deciding region won against.
    private static TemporaWorldRegion findTie(TemporaWorldRegion deciding, String loggerName, int dimID, double x,
        double y, double z) {
        LogRule decidingRule = deciding.getRule(loggerName);

        for (TemporaWorldRegion region : TemporaRegionRegistry.regionsContaining(dimID, x, y, z)) {
            LogRule rule = region.getRule(loggerName);
            if (rule != null && rule != decidingRule && region.getVolume() == deciding.getVolume()) {
                return region;
            }
        }
        return null;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandBase
                .getListOfStringsFromIterableMatchingLastWord(args, TemporaLoggerManager.getPositionalShortNames());
        }
        return null;
    }

    @Override
    public String getExampleArgs() {
        return "block";
    }
}
