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
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericLogger.FoundEvents;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;

/**
 * /tempora lookup [r:<radius>] [t:<time>] [l:<logger,...>] [p:<player,...>] [n:<count>]
 * Shows the latest events around the player, from every positional logger unless narrowed down with l:, and by anyone
 * unless narrowed down with p:. Global
 * loggers, which have no position, are looked up with /tempora global instead.
 */
public class LookupCommand extends TemporaSubcommand {

    private static final String DEFAULT_RADIUS = "10";
    private static final String DEFAULT_TIME = "3d";
    // Per logger. Each event is also drawn in the world, so this stays well below /tempora global's.
    public static final int DEFAULT_COUNT = 5;
    // Only so the search box can't overflow an int: centre (at most 30 million out, the world border) + radius + 1.
    private static final int MAX_RADIUS = 2_000_000_000;

    public LookupCommand() {
        super("lookup", "[r:<radius>]", "[t:<time>]", "[l:<logger,...>]", "[p:<player,...>]", "[n:<count>]");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        CommandFilters filters = CommandFilters.parse(this, sender, args, 0, "r", "t", "l", "p", "n");
        if (filters == null) return;

        int radius = CommandBase.parseIntBounded(sender, filters.get("r", DEFAULT_RADIUS), 1, MAX_RADIUS);
        TimeWindow window = TimeWindow.parse(filters.get("t", DEFAULT_TIME));
        String playerNames = filters.get("p");
        PlayerFilter players = playerNames == null ? PlayerFilter.anyone() : PlayerFilter.parse(playerNames);
        int count = parseCountPerLogger(sender, filters.get("n", String.valueOf(DEFAULT_COUNT)));

        List<GenericPositionalLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        SearchSummary summary = summarise(filters);

        showEvents(
            player,
            loggers,
            // The block the player stands in. Rounding would pick the next block over once past its middle.
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ),
            radius,
            window,
            players,
            count,
            summary.line(true),
            summary.line(false));
    }

    // Every filter, as typed or its default, for the line under the results.
    public SearchSummary summarise(CommandFilters filters) {
        return new SearchSummary(this, filters).add("r", DEFAULT_RADIUS)
            .add("t", DEFAULT_TIME)
            .add("l", "all")
            .add("p", "anyone")
            .add("n", String.valueOf(DEFAULT_COUNT));
    }

    // Comma separated positional logger names, or every positional logger if null. Null, after telling the sender,
    // if a name is unknown or a global logger.
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

    // Looks up each logger's latest events near a position in the player's dimension, up to count of each, on the query
    // thread, then shows them followed by afterResults, if any, or just nothingFound if none of them had any. A radius
    // of 0 means exactly that block. Loggers whose events have no player are left out when filtering by player.
    public static void showEvents(EntityPlayerMP player, Collection<GenericPositionalLogger<?>> loggers, int x, int y,
        int z, int radius, TimeWindow window, PlayerFilter players, int count, @Nullable IChatComponent afterResults,
        IChatComponent nothingFound) {
        int dimension = player.dimension;

        TemporaQueries.submit(player, () -> {
            List<FoundEvents> found = new ArrayList<>();
            for (GenericPositionalLogger<?> logger : loggers) {
                // A logger that is disabled, or failed to start, has no database to read.
                if (logger.notStarted()) continue;

                found.add(logger.findEventsNear(dimension, x, y, z, radius, window, players, count));
            }
            return found;
        }, (sender, found) -> {
            int shown = 0;
            for (FoundEvents events : found) {
                shown += events.show(sender);
            }

            if (shown > 0) {
                if (afterResults != null) sender.addChatMessage(afterResults);
            } else {
                nothingFound.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(nothingFound);
            }
        });
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
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    public String getExampleArgs() {
        return "r:25 t:3d-1d l:break,place n:10";
    }
}
