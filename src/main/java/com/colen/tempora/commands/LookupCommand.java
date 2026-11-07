package com.colen.tempora.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.SearchSummary;
import com.colen.tempora.commands.command_base.SortOrder;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.colen.tempora.utils.TimeWindow;

// /tempora lookup [r:<radius>] [t:<time>] [l:<logger,...>] [p:<player,...>] [n:<count>] [s:<sort>]
// Latest events around the player from every positional logger unless narrowed with l:, by anyone unless narrowed with
// p:. Listed under each logger, or with s:time all together in the order they happened. Global loggers have no position
// so they use /tempora global instead
public class LookupCommand extends TemporaSubcommand {

    private static final String DEFAULT_RADIUS = "10";
    private static final String DEFAULT_TIME = "3d";
    static final long DAY_SECONDS = 86_400;
    // Per logger. Each event gets drawn in the world too so keep this well below /tempora global's
    public static final int DEFAULT_COUNT = 5;
    // In total with s:time, shared between every logger
    public static final int DEFAULT_COUNT_BY_TIME = 10;
    // Only so the search box can't overflow an int, centre (30 million out at most, the world border) + radius + 1
    private static final int MAX_RADIUS = 2_000_000_000;

    public LookupCommand() {
        super(
            "lookup",
            "[r:<radius>]",
            "[t:<time>]",
            "[l:<logger,...>]",
            "[p:<player,...>]",
            "[n:<count>]",
            "[s:<sort>]");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        CommandFilters filters = CommandFilters.parse(this, sender, args, 0, "r", "t", "l", "p", "n", "s");
        if (filters == null) return;

        SortOrder order = SortOrder.parse(sender, filters.get("s", SortOrder.DEFAULT));
        if (order == null) return;

        int radius = CommandBase.parseIntBounded(sender, filters.get("r", DEFAULT_RADIUS), 1, MAX_RADIUS);
        TimeWindow window = TimeWindow.parse(filters.get("t", DEFAULT_TIME));
        String playerNames = filters.get("p");
        PlayerFilter players = playerNames == null ? PlayerFilter.anyone() : PlayerFilter.parse(playerNames);
        int count = parseCount(sender, filters.get("n", String.valueOf(defaultCount(order))), order);

        List<GenericPositionalLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        SearchSummary summary = summarise(filters, order);

        showEvents(
            player,
            loggers,
            // Block the player stands in, rounding would pick the next block over once past its middle
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ),
            radius,
            window,
            players,
            count,
            order,
            summary.line(true),
            summary.line(false));
    }

    // Every filter as typed or its default, for the line under the results
    private SearchSummary summarise(CommandFilters filters, SortOrder order) {
        return new SearchSummary(this, filters).add("r", DEFAULT_RADIUS)
            .add("t", DEFAULT_TIME)
            .add("l", "all")
            .add("p", "anyone")
            .add("n", String.valueOf(defaultCount(order)))
            .add("s", SortOrder.DEFAULT);
    }

    // n: default, per logger or in total with s:time
    public static int defaultCount(SortOrder order) {
        return order == SortOrder.TIME ? DEFAULT_COUNT_BY_TIME : DEFAULT_COUNT;
    }

    // Comma separated positional logger names, or every positional logger if null. Returns null after telling the
    // sender if a name is unknown or global
    public static @Nullable List<GenericPositionalLogger<?>> parseLoggers(ICommandSender sender,
        @Nullable String names) {
        if (names == null) return TemporaLoggerManager.getPositionalLoggers();

        List<GenericPositionalLogger<?>> loggers = new ArrayList<>();
        for (String name : names.split(",")) {
            GenericLogger<?> logger = TemporaLoggerManager.findLogger(name);
            if (logger instanceof GenericGlobalLogger) {
                sendError(
                    sender,
                    "tempora.command.lookup.global_logger",
                    logger.getShortName(),
                    TemporaCommand.GLOBAL.command("l:" + logger.getShortName()));
                return null;
            }
            if (!(logger instanceof GenericPositionalLogger<?>positional)) {
                sendUnknownLogger(sender, name, TemporaLoggerManager.getPositionalShortNames());
                return null;
            }
            if (!loggers.contains(positional)) loggers.add(positional);
        }
        return loggers;
    }

    public static void sendUnknownLogger(ICommandSender sender, String name, List<String> choices) {
        sendError(sender, "tempora.command.unknown_logger", name, String.join(", ", choices));
    }

    // Looks up each logger's latest events near a position in the player's dimension, up to count each, on the query
    // thread. Then shows them followed by afterResults if any, or just nothingFound if nothing had any. Sorted by time,
    // count is the total. Radius 0 is just that block. Loggers with no player get left out when filtering by player
    public static void showEvents(EntityPlayerMP player, Collection<GenericPositionalLogger<?>> loggers, int x, int y,
        int z, int radius, TimeWindow window, PlayerFilter players, int count, SortOrder order,
        @Nullable IChatComponent afterResults, IChatComponent nothingFound) {
        int dimension = player.dimension;

        TemporaQueries.submit(player, () -> {
            List<FoundEvents<?>> found = new ArrayList<>();
            for (GenericPositionalLogger<?> logger : loggers) {
                // Disabled or failed to start, no db to read
                if (logger.notStarted()) continue;

                found.add(logger.findEventsNear(dimension, x, y, z, radius, window, players, count));
            }
            return found;
        }, (sender, found) -> {
            int shown = show(sender, found, count, order);

            if (shown > 0) {
                if (afterResults != null) sender.addChatMessage(afterResults);
            } else {
                nothingFound.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(nothingFound);
            }
        });
    }

    // Shows what each logger found, under each logger or all together by time up to count total. Returns how many were
    // shown
    public static int show(ICommandSender sender, List<FoundEvents<?>> found, int count, SortOrder order) {
        if (order == SortOrder.TIME) return FoundEvents.showByTime(sender, found, count);

        int shown = 0;
        for (FoundEvents<?> events : found) {
            shown += events.show(sender);
        }
        return shown;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        suggestions.put("r", Arrays.asList("5", "10", "25", "50"));
        suggestions.put("t", Arrays.asList("1h", "1d", "3d", "1w", "1mo", "3d-1d"));
        suggestions.put("l", TemporaLoggerManager.getPositionalShortNames());
        List<String> names = PlayerUtils.getTabCompletionForPlayerNames("");
        List<String> playerSuggestions = new ArrayList<>(names);
        for (String name : names) playerSuggestions.add("!" + name);
        suggestions.put("p", playerSuggestions);
        suggestions.put("n", Arrays.asList("5", "10", "25", "50"));
        suggestions.put("s", SortOrder.choices());
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    protected Object[] getDescriptionArgs() {
        return new Object[] { Integer.parseInt(DEFAULT_RADIUS), TimeUtils.convertToSeconds(DEFAULT_TIME) / DAY_SECONDS,
            DEFAULT_COUNT };
    }

    @Override
    protected Object[] getArgumentArgs(int index) {
        return switch (index) {
            case 0 -> new Object[] { Integer.parseInt(DEFAULT_RADIUS) };
            case 4 -> new Object[] { DEFAULT_COUNT, DEFAULT_COUNT_BY_TIME, CommandsConfig.maxResultsPerLogger };
            default -> super.getArgumentArgs(index);
        };
    }

    @Override
    public String getExampleArgs() {
        return "r:25 t:3d-1d l:break,place n:10 s:time";
    }
}
