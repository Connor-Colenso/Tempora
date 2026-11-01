package com.colen.tempora.commands;

import static com.colen.tempora.utils.ChatUtils.createCenteredLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.DatabaseUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

/**
 * /tempora status
 * Shows whether each logger is storing events, how many it holds and how old they are, its size on disk and any
 * backlog, then the total size of Tempora's databases and the free space left for them. Works from anywhere, including
 * the server console.
 */
public class StatusCommand extends TemporaSubcommand {

    // Below this many waiting events a backlog is normal, a batch being written.
    private static final int QUEUE_WORTH_SHOWING = 100;
    // Free space under this is shown in red: a full disk stops every logger storing events.
    private static final long LOW_FREE_BYTES = 1024L * 1024 * 1024;

    public StatusCommand() {
        super("status");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length > 0) {
            sendUsageError(sender);
            return;
        }

        List<GenericLogger<?>> loggers = new ArrayList<>(TemporaLoggerManager.getLoggerList());
        loggers.sort(Comparator.comparing(GenericLogger::getShortName));
        Path directory = DatabaseUtils.databaseDir();

        TemporaQueries.submit(sender, () -> {
            Map<GenericLogger<?>, Stored> stored = new LinkedHashMap<>();
            for (GenericLogger<?> logger : loggers) {
                // A logger that is disabled, or failed to start, has no database to read.
                if (logger.notStarted()) continue;

                stored.put(logger, Stored.read(logger, directory));
            }
            return new Disk(stored, directory);
        }, (target, disk) -> {
            target.addChatMessage(createCenteredLine(getPath()));
            for (GenericLogger<?> logger : loggers) {
                target.addChatMessage(loggerLine(logger, disk.stored.get(logger)));
            }
            target.addChatMessage(disk.line());
        });
    }

    // E.g. "break [OK] 12,345 events, 1.2 MB, oldest 3.9d ago, newest 5s ago". Stored is null if it isn't started.
    private static IChatComponent loggerLine(GenericLogger<?> logger, @Nullable Stored stored) {
        IChatComponent name = EventChat.text(logger.getShortName(), EnumChatFormatting.GOLD);
        name.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    // How long events are kept is only known once it has started.
                    stored == null ? logger.getDescription() : logger.getInfoHover()));

        if (stored == null) {
            return EventChat.line(
                "tempora.command.status.logger.stopped",
                name,
                logger.isEnabledByConfig() ? state("not_started", EnumChatFormatting.RED, null)
                    : state("off", EnumChatFormatting.DARK_GRAY, null));
        }

        String failure = logger.getFailure();
        IChatComponent state = failure == null ? state("ok", EnumChatFormatting.GREEN, null)
            : state("failing", EnumChatFormatting.RED, failure);

        IChatComponent line;
        if (stored.error != null) {
            line = EventChat.line(
                "tempora.command.status.logger.unreadable",
                name,
                state,
                size(stored.bytes),
                EventChat.text(stored.error, EnumChatFormatting.RED));
        } else if (stored.events == 0) {
            line = EventChat.line("tempora.command.status.logger.empty", name, state, size(stored.bytes));
        } else {
            IChatComponent count = new ChatComponentNumber(stored.events);
            count.getChatStyle()
                .setColor(EnumChatFormatting.WHITE);
            IChatComponent events = new ChatComponentTranslation(
                stored.events == 1 ? "tempora.command.status.events.one" : "tempora.command.status.events.many",
                count);
            line = EventChat.line(
                "tempora.command.status.logger",
                name,
                state,
                events,
                size(stored.bytes),
                EventChat.time(stored.oldest),
                EventChat.time(stored.newest));
        }

        int queued = logger.getQueueSize();
        if (queued >= QUEUE_WORTH_SHOWING) {
            IChatComponent backlog = new ChatComponentTranslation(
                "tempora.command.status.queued",
                new ChatComponentNumber(queued));
            backlog.getChatStyle()
                .setColor(EnumChatFormatting.GOLD);
            line.appendText(" ")
                .appendSibling(backlog);
        }
        return line;
    }

    // E.g. "[OK]", with why on hover if there is a reason.
    private static IChatComponent state(String key, EnumChatFormatting colour, @Nullable String reason) {
        IChatComponent state = new ChatComponentTranslation("tempora.command.status.state." + key);
        IChatComponent hover = reason == null
            ? new ChatComponentTranslation("tempora.command.status.state." + key + ".hover")
            : new ChatComponentTranslation("tempora.command.status.state." + key + ".hover", reason);
        state.getChatStyle()
            .setColor(colour)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        return state;
    }

    // E.g. "1.2 MB", in binary units as file managers show them on Windows.
    static IChatComponent size(long bytes) {
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        String text = unit == 0 ? bytes + " B" : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
        return EventChat.text(text, EnumChatFormatting.WHITE);
    }

    private static long fileSize(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /** What one logger's database holds, read on the query thread. */
    private static final class Stored {

        long events, oldest, newest, bytes;
        @Nullable
        String error;

        static Stored read(GenericLogger<?> logger, Path directory) {
            Stored stored = new Stored();
            String table = logger.getLoggerName();
            // With its write-ahead log, which holds the newest events until they are copied into the database.
            stored.bytes = fileSize(directory.resolve(table + ".db")) + fileSize(directory.resolve(table + ".db-wal"));

            // One aggregate per subquery, so SQLite reads each from the end of an index rather than the whole table.
            // eventIDs count up as events are written and only the oldest are ever deleted, so they give the count.
            String sql = "SELECT (SELECT MIN(timestamp) FROM " + table
                + "), (SELECT MAX(timestamp) FROM "
                + table
                + "), (SELECT MAX(eventID) FROM "
                + table
                + ") - (SELECT MIN(eventID) FROM "
                + table
                + ") + 1";
            try (Statement st = TemporaQueries.connection(logger)
                .createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                stored.oldest = rs.getLong(1);
                stored.newest = rs.getLong(2);
                // NULL, read as 0, when the table is empty.
                stored.events = rs.getLong(3);
            } catch (SQLException e) {
                TemporaQueries.throwIfTimedOut();
                stored.error = e.getMessage();
            }
            return stored;
        }
    }

    /** Every started logger's database, and the folder they are in. */
    private static final class Disk {

        final Map<GenericLogger<?>, Stored> stored;
        final Path directory;
        final long totalBytes, freeBytes;

        Disk(Map<GenericLogger<?>, Stored> stored, Path directory) throws IOException {
            this.stored = stored;
            this.directory = directory;

            // Every file, so the shared players database and old loggers' files count too.
            long total = 0;
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : (Iterable<Path>) files::iterator) total += fileSize(file);
            }
            totalBytes = total;
            freeBytes = directory.toFile()
                .getUsableSpace();
        }

        // E.g. "Total 1.2 GB on disk, 50.3 GB free", with the folder on hover.
        IChatComponent line() {
            IChatComponent where = new ChatComponentText(
                directory.toAbsolutePath()
                    .toString());
            where.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);

            IChatComponent free = size(freeBytes);
            if (freeBytes < LOW_FREE_BYTES) free.getChatStyle()
                .setColor(EnumChatFormatting.RED);

            IChatComponent line = EventChat.line("tempora.command.status.total", size(totalBytes), free);
            line.getChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, where));
            return line;
        }
    }

    @Override
    public String getExampleArgs() {
        return "";
    }
}
