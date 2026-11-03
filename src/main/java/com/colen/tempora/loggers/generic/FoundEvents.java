package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

/**
 * One logger's part of a lookup: found on the query thread (see {@link TemporaQueries}), then shown on the server
 * thread, either under the logger's own header with {@link #show}, or merged with other loggers' in the order they
 * happened with {@link #showByTime}.
 */
public final class FoundEvents<EventInfo extends GenericEventInfo> {

    private final GenericLogger<EventInfo> logger;
    // Newest first, as queried. Empty if the query failed.
    private final List<EventInfo> newestFirst;
    // How many the query was limited to, so the header can say when there may be more.
    private final int limit;
    // Clicking the logger's name fills this in, to search this logger alone.
    private final String narrowCommand;
    private final @Nullable SQLException failure;

    private FoundEvents(GenericLogger<EventInfo> logger, List<EventInfo> newestFirst, int limit, String narrowCommand,
        @Nullable SQLException failure) {
        this.logger = logger;
        this.newestFirst = newestFirst;
        this.limit = limit;
        this.narrowCommand = narrowCommand;
        this.failure = failure;
    }

    static <E extends GenericEventInfo> FoundEvents<E> found(GenericLogger<E> logger, List<E> newestFirst, int limit,
        String narrowCommand) {
        return new FoundEvents<>(logger, newestFirst, limit, narrowCommand, null);
    }

    // A lookup that can't find anything in this logger, e.g. filtering by player in a logger with no player.
    static <E extends GenericEventInfo> FoundEvents<E> none(GenericLogger<E> logger) {
        return new FoundEvents<>(logger, Collections.emptyList(), 0, "", null);
    }

    static <E extends GenericEventInfo> FoundEvents<E> failed(GenericLogger<E> logger, SQLException e) {
        return new FoundEvents<>(logger, Collections.emptyList(), 0, "", e);
    }

    /**
     * Sends them to the sender under this logger's header and draws those in the sender's dimension. Says nothing if
     * there are none, so callers checking many loggers can report that once. Returns how many were shown.
     */
    public int show(ICommandSender sender) {
        if (failure != null) {
            logger.sendQueryFailed(sender, failure);
            return 0;
        }

        // So one broken logger doesn't stop the others' results being shown.
        try {
            logger.sendEventsInChat(sender, newestFirst, limit, narrowCommand);
            logger.drawInWorld(sender, newestFirst);
            return newestFirst.size();
        } catch (RuntimeException e) {
            logger.sendShowFailed(sender, e);
            return 0;
        }
    }

    /**
     * Shows the newest {@code limit} events of all of them together under one header, in the order they happened with
     * the newest at the bottom, each tagged with its logger, and draws those in the sender's dimension. Failed queries
     * are reported first. Says nothing else if there are none. Returns how many were shown.
     */
    public static int showByTime(ICommandSender sender, List<FoundEvents<?>> found, int limit) {
        for (FoundEvents<?> events : found) {
            if (events.failure != null) events.logger.sendQueryFailed(sender, events.failure);
        }

        List<Integer> sources = pickNewest(found, limit);
        if (sources.isEmpty()) return 0;

        int[] kept = new int[found.size()];
        for (int i : sources) kept[i]++;

        // E.g. "Events by time (latest 10)".
        IChatComponent name = new ChatComponentTranslation("tempora.command.lookup.timeline");
        name.getChatStyle()
            .setColor(EnumChatFormatting.GOLD);
        IChatComponent header = new ChatComponentTranslation(
            "%s %s",
            name,
            GenericLogger.countLabel(sources.size(), limit));
        header.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(header);

        IChatComponent[] tags = new IChatComponent[found.size()];
        for (int i = 0; i < found.size(); i++) {
            if (kept[i] == 0) continue;
            found.get(i).logger.sendPendingWarning(sender);
            tags[i] = found.get(i)
                .tag();
        }

        // Newest at the bottom, where chat is read from. Walking back through the picks, each logger's come out oldest
        // first, so a logger's next event is at one less than its count.
        int[] next = kept.clone();
        for (int p = sources.size() - 1; p >= 0; p--) {
            int i = sources.get(p);
            GenericEventInfo event = found.get(i).newestFirst.get(--next[i]);
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "%s %s",
                    tags[i].createCopy(),
                    GenericLogger.chatLineOrPlaceholder(event)));
        }

        for (int i = 0; i < found.size(); i++) {
            if (kept[i] > 0) found.get(i)
                .drawFirst(sender, kept[i]);
        }

        return sources.size();
    }

    /** The events {@link #showByTime} shows, newest first. */
    public static List<GenericEventInfo> newestAcross(List<FoundEvents<?>> found, int limit) {
        int[] next = new int[found.size()];
        List<GenericEventInfo> events = new ArrayList<>();
        for (int i : pickNewest(found, limit)) {
            events.add(found.get(i).newestFirst.get(next[i]++));
        }
        return events;
    }

    /**
     * Which logger, by its index in found, each of the newest limit events of all of them together comes from, newest
     * first. Each logger's events are newest first, so taking the newest of the loggers' next events each time takes
     * the first few of each, in order. Ties go to the logger listed first.
     */
    private static List<Integer> pickNewest(List<FoundEvents<?>> found, int limit) {
        int[] taken = new int[found.size()];
        List<Integer> sources = new ArrayList<>();
        while (sources.size() < limit) {
            int newest = -1;
            for (int i = 0; i < found.size(); i++) {
                if (taken[i] == found.get(i).newestFirst.size()) continue;
                if (newest < 0 || timestamp(found, i, taken[i]) > timestamp(found, newest, taken[newest])) newest = i;
            }
            if (newest < 0) break;

            sources.add(newest);
            taken[newest]++;
        }
        return sources;
    }

    // The timestamp of the event at index, newest first, of logger i.
    private static long timestamp(List<FoundEvents<?>> found, int i, int index) {
        return found.get(i).newestFirst.get(index).timestamp;
    }

    // This logger's tag before each of its events, or just its short name in brackets if another mod's logger throws
    // making it, so its events still show.
    private IChatComponent tag() {
        try {
            return logger.timelineTag(narrowCommand);
        } catch (RuntimeException e) {
            LOG.error("Could not make the lookup tag for {}.", logger.getLoggerName(), e);
            return new ChatComponentText("[" + logger.getShortName() + "]");
        }
    }

    private void drawFirst(ICommandSender sender, int count) {
        try {
            logger.drawInWorld(sender, newestFirst.subList(0, count));
        } catch (RuntimeException e) {
            LOG.error("Could not draw {}'s lookup results.", logger.getLoggerName(), e);
        }
    }
}
