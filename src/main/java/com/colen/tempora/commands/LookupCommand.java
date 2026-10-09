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
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.TimeUtils;

/**
 * /tempora lookup [r:<radius>] [t:<time>] [l:<logger,...>]
 * Shows the latest events around the player, from every logger unless narrowed down with l:.
 */
public class LookupCommand extends TemporaSubcommand {

    private static final String DEFAULT_RADIUS = "10";
    private static final String DEFAULT_TIME = "3d";

    public LookupCommand() {
        super("lookup", "[r:<radius>]", "[t:<time>]", "[l:<logger,...>]");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        CommandFilters filters = CommandFilters.parse(sender, args, 0, "r", "t", "l");
        if (filters == null) return;

        int radius = CommandBase.parseIntWithMin(sender, filters.get("r", DEFAULT_RADIUS), 1);
        String time = filters.get("t", DEFAULT_TIME);
        long seconds = TimeUtils.convertToSeconds(time);

        List<GenericPositionalLogger<?>> loggers = parseLoggers(sender, filters.get("l"));
        if (loggers == null) return;

        showEvents(
            player,
            loggers,
            (int) Math.round(player.posX),
            (int) Math.round(player.posY),
            (int) Math.round(player.posZ),
            radius,
            seconds,
            new ChatComponentTranslation("tempora.command.lookup.nothing_found", radius, time));
    }

    // Comma separated logger names, or every logger if null. Null, after telling the sender, if a name is unknown.
    public static @Nullable List<GenericPositionalLogger<?>> parseLoggers(ICommandSender sender,
        @Nullable String names) {
        if (names == null) return new ArrayList<>(TemporaLoggerManager.getLoggerList());

        List<GenericPositionalLogger<?>> loggers = new ArrayList<>();
        for (String name : names.split(",")) {
            GenericPositionalLogger<?> logger = TemporaLoggerManager.findLogger(name);
            if (logger == null) {
                sendUnknownLogger(sender, name);
                return null;
            }
            if (!loggers.contains(logger)) loggers.add(logger);
        }
        return loggers;
    }

    public static void sendUnknownLogger(ICommandSender sender, String name) {
        sendError(
            sender,
            "tempora.command.unknown_logger",
            name,
            String.join(", ", TemporaLoggerManager.getAllShortNames()));
    }

    // Shows each logger's events near a position, then a single "nothing found" line if none of them had any. A
    // radius of 0 means exactly that block, negative seconds means no time limit.
    public static void showEvents(EntityPlayerMP player, Collection<GenericPositionalLogger<?>> loggers, int x, int y,
        int z, int radius, long seconds, IChatComponent nothingFound) {
        int found = 0;
        for (GenericPositionalLogger<?> logger : loggers) {
            // A disabled logger has no database to read.
            if (!logger.isEnabledByConfig()) continue;

            found += logger.getDatabaseManager()
                .showEventsNear(player, x, y, z, radius, seconds, player.dimension);
        }

        if (found == 0) {
            nothingFound.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            player.addChatMessage(nothingFound);
        }
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        suggestions.put("r", Arrays.asList("5", "10", "25", "50"));
        suggestions.put("t", Arrays.asList("1h", "1d", "3d", "1w", "1mo"));
        suggestions.put("l", TemporaLoggerManager.getAllShortNames());
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    public String getExampleArgs() {
        return "r:25 t:1d l:break,place";
    }
}
