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
import com.colen.tempora.commands.command_base.SortOrder;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;

// /tempora player <player,...> [t:<time>] [l:<logger,...>] [n:<count>] [s:<sort>]
// Latest events of one or more players wherever they happened (a ! leaves a player out, same as p: in a lookup), from
// every logger that records players unless narrowed with l:. Under each logger or with s:time all together in order.
// Works from anywhere, console too. Players also get the ones in their dimension drawn in the world
public class PlayerCommand extends TemporaSubcommand {

    private static final int DEFAULT_COUNT = LookupCommand.DEFAULT_COUNT;

    public PlayerCommand() {
        super("player", "<player>", "[t:<time>]", "[l:<logger,...>]", "[n:<count>]", "[s:<sort>]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsageError(sender);
            return;
        }

        PlayerFilter players = PlayerFilter.parse(args[0]);

        CommandFilters filters = CommandFilters.parse(this, sender, args, 1, "t", "l", "n", "s");
        if (filters == null) return;

        SortOrder order = SortOrder.parse(sender, filters.get("s", SortOrder.DEFAULT));
        if (order == null) return;

        String time = filters.get("t");
        TimeWindow window = time == null ? TimeWindow.allTime() : TimeWindow.parse(time);
        String defaultCount = String.valueOf(LookupCommand.defaultCount(order));
        int count = parseCount(sender, filters.get("n", defaultCount), order);

        List<GenericLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        SearchSummary summary = new SearchSummary(this, filters).add("t", "all")
            .add("l", "all")
            .add("n", defaultCount)
            .add("s", SortOrder.DEFAULT);

        TemporaQueries.submit(sender, () -> {
            List<FoundEvents<?>> found = new ArrayList<>();
            for (GenericLogger<?> logger : loggers) {
                // Disabled or failed to start, no db to read
                if (logger.notStarted()) continue;

                found.add(logger.findEventsBy(players, window, count, command(args[0], "l:" + logger.getShortName())));
            }
            return found;
        }, (target, found) -> {
            target.addChatMessage(EventChat.line("tempora.command.player.header", players.describe()));

            int shown = LookupCommand.show(target, found, count, order);

            target.addChatMessage(summary.line(shown > 0));
        });
    }

    // Comma separated logger names, or every logger that records players if null. Returns null after telling the sender
    // if a name is unknown or doesn't record players
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
        // Same order every time
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
            // Keep everything up to the last comma, plus any ! on the name being typed
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
        suggestions.put("s", SortOrder.choices());
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    protected Object[] getDescriptionArgs() {
        return new Object[] { DEFAULT_COUNT };
    }

    @Override
    protected Object[] getArgumentArgs(int index) {
        if (index == 3) return new Object[] { DEFAULT_COUNT, LookupCommand.DEFAULT_COUNT_BY_TIME,
            CommandsConfig.maxResultsPerLogger };
        return super.getArgumentArgs(index);
    }

    @Override
    public String getExampleArgs() {
        return "Notch t:1d l:break,place,inventory n:20 s:time";
    }
}
