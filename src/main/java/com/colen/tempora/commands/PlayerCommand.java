package com.colen.tempora.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.SearchSummary;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;

/**
 * /tempora player <player,...> [t:<time>] [l:<logger,...>] [n:<count>]
 * Shows a player's latest events wherever they happened, or several players' (a ! leaves a player out instead, as p:
 * does in a lookup), from every logger that records players unless narrowed down
 * with l:. Works from anywhere, including the server console. Players also get the events in their dimension drawn in
 * the world.
 */
public class PlayerCommand extends TemporaSubcommand {

    private static final String DEFAULT_COUNT = "5";

    public PlayerCommand() {
        super("player", "<player>", "[t:<time>]", "[l:<logger,...>]", "[n:<count>]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsageError(sender);
            return;
        }

        PlayerFilter players = PlayerFilter.parse(args[0]);

        CommandFilters filters = CommandFilters.parse(this, sender, args, 1, "t", "l", "n");
        if (filters == null) return;

        String time = filters.get("t");
        TimeWindow window = time == null ? TimeWindow.allTime() : TimeWindow.parse(time);
        int count = parseCountPerLogger(sender, filters.get("n", DEFAULT_COUNT));

        List<GenericLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        SearchSummary summary = new SearchSummary(this, filters).add("t", "all")
            .add("l", "all")
            .add("n", DEFAULT_COUNT);

        TemporaQueries.submit(sender, () -> {
            List<FoundEvents> found = new ArrayList<>();
            for (GenericLogger<?> logger : loggers) {
                // A logger that is disabled, or failed to start, has no database to read.
                if (logger.notStarted()) continue;

                found.add(logger.findEventsBy(players, window, count, command(args[0], "l:" + logger.getShortName())));
            }
            return found;
        }, (target, found) -> {
            target.addChatMessage(EventChat.line("tempora.command.player.header", players.describe()));

            int shown = 0;
            for (FoundEvents events : found) {
                shown += events.show(target);
            }

            target.addChatMessage(summary.line(shown > 0));
        });
    }

    // Comma separated logger names, or every logger that records players if null. Null, after telling the sender, if
    // a name is unknown or a logger that doesn't record players.
    private static @Nullable List<GenericLogger<?>> parseLoggers(ICommandSender sender, @Nullable String names) {
        if (names == null) return playerLoggers();

        List<GenericLogger<?>> loggers = new ArrayList<>();
        for (String name : names.split(",")) {
            GenericLogger<?> logger = TemporaLoggerManager.findLogger(name);
            if (logger == null) {
                LookupCommand.sendUnknownLogger(sender, name, shortNames(playerLoggers()));
                return null;
            }
            if (!logger.hasPlayerColumn()) {
                sendError(
                    sender,
                    "tempora.command.player.no_player",
                    logger.getShortName(),
                    String.join(", ", shortNames(playerLoggers())));
                return null;
            }
            if (!loggers.contains(logger)) loggers.add(logger);
        }
        return loggers;
    }

    private static List<GenericLogger<?>> playerLoggers() {
        List<GenericLogger<?>> loggers = new ArrayList<>();
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (logger.hasPlayerColumn()) loggers.add(logger);
        }
        // So results come in the same order every time.
        loggers.sort(
            (a, b) -> a.getShortName()
                .compareTo(b.getShortName()));
        return loggers;
    }

    private static List<String> shortNames(List<GenericLogger<?>> loggers) {
        List<String> names = new ArrayList<>();
        for (GenericLogger<?> logger : loggers) names.add(logger.getShortName());
        return names;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            // Keep everything up to the last comma, and any ! on the name being typed.
            String typed = args[0];
            int nameStart = typed.lastIndexOf(',') + 1;
            if (typed.startsWith("!", nameStart)) nameStart++;

            String done = typed.substring(0, nameStart);
            List<String> completions = new ArrayList<>();
            for (String name : PlayerUtils.getTabCompletionForPlayerNames(typed.substring(nameStart))) {
                completions.add(done + name);
            }
            return completions;
        }

        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        suggestions.put("t", Arrays.asList("1h", "1d", "3d", "1w", "1mo", "3d-1d"));
        suggestions.put("l", shortNames(playerLoggers()));
        suggestions.put("n", Arrays.asList("5", "10", "25", "50"));
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    protected Object[] getDescriptionArgs() {
        return new Object[] { Integer.parseInt(DEFAULT_COUNT) };
    }

    @Override
    protected Object[] getArgumentArgs(int index) {
        if (index == 3) return new Object[] { Integer.parseInt(DEFAULT_COUNT), CommandsConfig.maxResultsPerLogger };
        return super.getArgumentArgs(index);
    }

    @Override
    public String getExampleArgs() {
        return "Notch t:1d l:break,place,inventory n:10";
    }
}
