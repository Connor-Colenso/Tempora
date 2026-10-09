package com.colen.tempora.commands.regions;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.CommandUtils;

/**
 * /tempora_region_rule <label> <logger|all> <on|off|clear>
 * Sets or clears a region's rule for a logger. See TemporaRegionRegistry for how rules from overlapping regions
 * combine.
 */
public class SetRegionRule extends TemporaCommandBase {

    public static final String COMMAND_NAME = "tempora_region_rule";
    private static final String ALL_LOGGERS = "all";
    private static final String CLEAR = "clear";

    public SetRegionRule() {
        super(
            new CommandArg("<label>", "tempora.command.region_rule.help.arg0"),
            new CommandArg("<logger|all>", "tempora.command.region_rule.help.arg1"),
            new CommandArg("<on|off|clear>", "tempora.command.region_rule.help.arg2"));
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
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
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        boolean allLoggers = args[1].equalsIgnoreCase(ALL_LOGGERS);
        String loggerName = null;
        if (!allLoggers) {
            loggerName = resolveLoggerName(args[1], region);
            if (loggerName == null) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.region_rule.unknown_logger",
                    args[1],
                    String.join(", ", TemporaLoggerManager.getAllLoggerNames()));
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
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

    // A null loggerName means every logger.
    private static void setRules(ICommandSender sender, TemporaWorldRegion region, String loggerName, LogRule rule) {
        List<String> loggerNames = loggerName == null ? TemporaLoggerManager.getAllLoggerNames()
            : Collections.singletonList(loggerName);

        for (String name : loggerNames) {
            TemporaRegionRegistry.setRule(region, name, rule);
        }

        IChatComponent msg = loggerName == null
            ? new ChatComponentTranslation(
                "tempora.command.region_rule.set_all",
                region.getLabel(),
                TemporaWorldRegion.ruleChatComponent(rule))
            : new ChatComponentTranslation(
                "tempora.command.region_rule.set",
                region.getLabel(),
                loggerName,
                TemporaWorldRegion.ruleChatComponent(rule));
        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);

        // A rule can't turn on a logger that is switched off entirely, so say so rather than leave them guessing.
        if (rule == LogRule.ON) {
            for (String name : loggerNames) {
                GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(name);
                if (logger != null && !logger.isEnabledByConfig()) {
                    IChatComponent warning = new ChatComponentTranslation(
                        "tempora.command.region_rule.logger_disabled",
                        name);
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
            msg = new ChatComponentTranslation("tempora.command.region_rule.cleared_all", region.getLabel());
        } else if (TemporaRegionRegistry.clearRule(region, loggerName)) {
            msg = new ChatComponentTranslation("tempora.command.region_rule.cleared", region.getLabel(), loggerName);
        } else {
            msg = new ChatComponentTranslation("tempora.command.region_rule.no_rule", region.getLabel(), loggerName);
        }

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

    // Matches registered loggers ignoring case. Also matches rules already on the region, so a rule for a logger
    // that has since been removed from the mod can still be cleared.
    private static String resolveLoggerName(String typed, TemporaWorldRegion region) {
        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLoggerIgnoreCase(typed);
        if (logger != null) return logger.getLoggerName();

        for (String existing : region.getRules()
            .keySet()) {
            if (existing.equalsIgnoreCase(typed)) return existing;
        }
        return null;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        switch (args.length) {
            case 1:
                return getListOfStringsFromIterableMatchingLastWord(args, TemporaRegionRegistry.getAllLabels());
            case 2:
                List<String> loggers = new ArrayList<>(TemporaLoggerManager.getAllLoggerNames());
                loggers.add(ALL_LOGGERS);
                return getListOfStringsFromIterableMatchingLastWord(args, loggers);
            case 3:
                return getListOfStringsMatchingLastWord(
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
    public String getExampleCommand() {
        return "/" + getCommandName() + " spawn BlockChangeLogger on";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.region_rule.help.description");
    }
}
