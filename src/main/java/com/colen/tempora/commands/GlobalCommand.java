package com.colen.tempora.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.TimeWindow;

/**
 * /tempora global [t:<time>] [l:<logger,...>] [n:<count>]
 * Shows the latest events from the global loggers, the ones not tied to a place such as weather and TPS, from every
 * global logger unless narrowed down with l:. Works from anywhere, including the server console.
 */
public class GlobalCommand extends TemporaSubcommand {

    private static final String DEFAULT_TIME = "3d";
    private static final String DEFAULT_COUNT = "10";
    private static final int MAX_COUNT = 100;

    public GlobalCommand() {
        super("global", "[t:<time>]", "[l:<logger,...>]", "[n:<count>]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        CommandFilters filters = CommandFilters.parse(sender, args, 0, "t", "l", "n");
        if (filters == null) return;

        TimeWindow window = TimeWindow.parse(filters.get("t", DEFAULT_TIME));
        int count = CommandBase.parseIntBounded(sender, filters.get("n", DEFAULT_COUNT), 1, MAX_COUNT);

        List<GenericGlobalLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        TemporaQueries.submit(sender, () -> {
            List<FoundEvents> found = new ArrayList<>();
            for (GenericGlobalLogger<?> logger : loggers) {
                // A logger that is disabled, or failed to start, has no database to read.
                if (logger.notStarted()) continue;

                found.add(logger.findLatestEvents(window, count));
            }
            return found;
        }, (target, found) -> {
            int shown = 0;
            for (FoundEvents events : found) {
                shown += events.show(target);
            }

            if (shown == 0) {
                IChatComponent nothingFound = new ChatComponentTranslation(
                    "tempora.command.global.nothing_found",
                    window.describe());
                nothingFound.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                target.addChatMessage(nothingFound);
            }
        });
    }

    // Comma separated global logger names, or every global logger if null. Null, after telling the sender, if a name
    // is unknown or a positional logger.
    private static @Nullable List<GenericGlobalLogger<?>> parseLoggers(ICommandSender sender, @Nullable String names) {
        if (names == null) return TemporaLoggerManager.getGlobalLoggers();

        List<GenericGlobalLogger<?>> loggers = new ArrayList<>();
        for (String name : names.split(",")) {
            GenericLogger<?> logger = TemporaLoggerManager.findLogger(name);
            if (logger instanceof GenericPositionalLogger) {
                sendError(
                    sender,
                    "tempora.command.global.positional_logger",
                    logger.getShortName(),
                    TemporaCommand.LOOKUP.command("l:" + logger.getShortName()));
                return null;
            }
            if (!(logger instanceof GenericGlobalLogger<?>global)) {
                LookupCommand.sendUnknownLogger(sender, name, TemporaLoggerManager.getGlobalShortNames());
                return null;
            }
            if (!loggers.contains(global)) loggers.add(global);
        }
        return loggers;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        suggestions.put("t", Arrays.asList("1h", "1d", "3d", "1w", "1mo", "3d-1d"));
        suggestions.put("l", TemporaLoggerManager.getGlobalShortNames());
        suggestions.put("n", Arrays.asList("10", "25", "50", "100"));
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    public String getExampleArgs() {
        return "t:1d l:weather n:20";
    }
}
