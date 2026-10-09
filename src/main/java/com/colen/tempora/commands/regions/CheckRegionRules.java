package com.colen.tempora.commands.regions;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;
import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
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
 * /tempora_region_check [logger]
 * Shows, for each logger, whether it logs at the player's position and what decided that.
 */
public class CheckRegionRules extends TemporaCommandBase {

    public CheckRegionRules() {
        super(new CommandArg("[logger]", "tempora.command.region_check.help.arg0"));
    }

    @Override
    public String getCommandName() {
        return "tempora_region_check";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        if (args.length > 1) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        List<GenericPositionalLogger<?>> loggers = new ArrayList<>();
        if (args.length == 1) {
            GenericPositionalLogger<?> logger = TemporaLoggerManager.getLoggerIgnoreCase(args[0]);
            if (logger == null) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.region_rule.unknown_logger",
                    args[0],
                    String.join(", ", TemporaLoggerManager.getAllLoggerNames()));
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
            loggers.add(logger);
        } else {
            loggers.addAll(TemporaLoggerManager.getLoggerList());
            loggers.sort(Comparator.comparing(GenericPositionalLogger::getLoggerName));
        }

        int dimID = player.dimension;
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;

        IChatComponent header = new ChatComponentTranslation(
            "tempora.command.region_check.header",
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
    private static IChatComponent explain(GenericPositionalLogger<?> logger, int dimID, double x, double y,
        double z) {
        String loggerName = logger.getLoggerName();

        LogRule result;
        IChatComponent reason;

        if (!logger.isEnabledByConfig()) {
            result = LogRule.OFF;
            reason = new ChatComponentTranslation("tempora.command.region_check.reason.disabled");
        } else {
            TemporaWorldRegion deciding = TemporaRegionRegistry.findDecidingRegion(loggerName, dimID, x, y, z);

            if (deciding == null) {
                result = logger.logsByDefault() ? LogRule.ON : LogRule.OFF;
                reason = new ChatComponentTranslation("tempora.command.region_check.reason.default");
            } else {
                result = deciding.getRule(loggerName);
                TemporaWorldRegion tiedWith = findTie(deciding, loggerName, dimID, x, y, z);

                reason = tiedWith == null
                    ? new ChatComponentTranslation("tempora.command.region_check.reason.region", deciding.getLabel())
                    : new ChatComponentTranslation(
                        "tempora.command.region_check.reason.tie",
                        deciding.getLabel(),
                        tiedWith.getLabel());
            }
        }

        reason.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        return new ChatComponentTranslation(
            "tempora.command.region_check.entry",
            loggerName,
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
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null;
    }

    @Override
    public String getExampleCommand() {
        return "/" + getCommandName() + " BlockChangeLogger";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.region_check.help.description");
    }
}
