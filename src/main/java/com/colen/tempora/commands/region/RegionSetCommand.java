package com.colen.tempora.commands.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
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
 * /tempora region set <name> <logger|all> <on|off|clear>
 * Sets or clears a region's rule for a positional logger. See TemporaRegionRegistry for how rules from overlapping
 * regions combine. Global loggers have no position, so regions never affect them.
 */
public class RegionSetCommand extends TemporaSubcommand {

    private static final String ALL_LOGGERS = "all";
    private static final String CLEAR = "clear";

    public RegionSetCommand() {
        super("set", "<name>", "<logger|all>", "<on|off|clear>");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsageError(sender);
            return;
        }

        TemporaWorldRegion region = TemporaRegionRegistry.getByLabel(args[0]);
        if (region == null) {
            sender.addChatMessage(CommandUtils.regionNotFound(args[0]));
            return;
        }

        boolean clear = args[2].equalsIgnoreCase(CLEAR);
        LogRule rule = LogRule.fromString(args[2]);
        if (!clear && rule == null) {
            sendUsageError(sender);
            return;
        }

        boolean allLoggers = args[1].equalsIgnoreCase(ALL_LOGGERS);
        String loggerName = null;
        if (!allLoggers) {
            if (TemporaLoggerManager.findLogger(args[1]) instanceof GenericGlobalLogger<?>global) {
                sendError(sender, "tempora.command.region.global_logger", global.getShortName());
                return;
            }

            loggerName = resolveLoggerName(args[1], region);
            if (loggerName == null) {
                LookupCommand.sendUnknownLogger(sender, args[1], TemporaLoggerManager.getPositionalShortNames());
                return;
            }
        }

        if (clear) {
            clearRules(sender, region, loggerName);
        } else {
            setRules(sender, region, loggerName, rule);
        }

        sender.addChatMessage(region.getRulesChatComponent());
    }

    // A null loggerName means every positional logger.
    private static void setRules(ICommandSender sender, TemporaWorldRegion region, String loggerName, LogRule rule) {
        List<String> loggerNames = new ArrayList<>();
        if (loggerName == null) {
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getPositionalLoggers()) {
                loggerNames.add(logger.getLoggerName());
            }
        } else {
            loggerNames.add(loggerName);
        }

        for (String name : loggerNames) {
            TemporaRegionRegistry.setRule(region, name, rule);
        }

        IChatComponent msg = loggerName == null
            ? new ChatComponentTranslation(
                "tempora.command.region.set.set_all",
                region.getLabel(),
                TemporaWorldRegion.ruleChatComponent(rule))
            : new ChatComponentTranslation(
                "tempora.command.region.set.set",
                region.getLabel(),
                TemporaLoggerManager.displayName(loggerName),
                TemporaWorldRegion.ruleChatComponent(rule));
        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);

        // A rule can't turn on a logger that is switched off entirely, so say so rather than leave them guessing.
        if (rule == LogRule.ON) {
            for (String name : loggerNames) {
                GenericLogger<?> logger = TemporaLoggerManager.getLogger(name);
                if (logger != null && !logger.isEnabledByConfig()) {
                    IChatComponent warning = new ChatComponentTranslation(
                        "tempora.command.region.set.logger_disabled",
                        logger.getShortName(),
                        logger.getLoggerName());
                    warning.getChatStyle()
                        .setColor(EnumChatFormatting.GOLD);
                    sender.addChatMessage(warning);
                }
            }
        }
    }

    // A null loggerName means every logger.
    private static void clearRules(ICommandSender sender, TemporaWorldRegion region, String loggerName) {
        IChatComponent msg;

        if (loggerName == null) {
            for (String name : new ArrayList<>(
                region.getRules()
                    .keySet())) {
                TemporaRegionRegistry.clearRule(region, name);
            }
            msg = new ChatComponentTranslation("tempora.command.region.set.cleared_all", region.getLabel());
        } else if (TemporaRegionRegistry.clearRule(region, loggerName)) {
            msg = new ChatComponentTranslation(
                "tempora.command.region.set.cleared",
                region.getLabel(),
                TemporaLoggerManager.displayName(loggerName));
        } else {
            msg = new ChatComponentTranslation(
                "tempora.command.region.set.no_rule",
                region.getLabel(),
                TemporaLoggerManager.displayName(loggerName));
        }

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

    // Matches registered positional loggers by short or full name. Also matches rules already on the region, so a rule
    // for a logger that has since been removed from the mod can still be cleared.
    private static String resolveLoggerName(String typed, TemporaWorldRegion region) {
        if (TemporaLoggerManager.findLogger(typed) instanceof GenericPositionalLogger<?>logger) {
            return logger.getLoggerName();
        }

        for (String existing : region.getRules()
            .keySet()) {
            if (existing.equalsIgnoreCase(typed)) return existing;
        }
        return null;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        switch (args.length) {
            case 1:
                return CommandBase
                    .getListOfStringsFromIterableMatchingLastWord(args, TemporaRegionRegistry.getAllLabels());
            case 2:
                List<String> loggers = new ArrayList<>(TemporaLoggerManager.getPositionalShortNames());
                loggers.add(ALL_LOGGERS);
                return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, loggers);
            case 3:
                return CommandBase.getListOfStringsMatchingLastWord(
                    args,
                    LogRule.ON.name()
                        .toLowerCase(Locale.ROOT),
                    LogRule.OFF.name()
                        .toLowerCase(Locale.ROOT),
                    CLEAR);
            default:
                return null;
        }
    }

    @Override
    public String getExampleArgs() {
        return "spawn block on";
    }
}
