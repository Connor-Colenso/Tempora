package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;
import org.sqlite.ProgressHandler;

import com.colen.tempora.config.CommandsConfig;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// Runs lookups (/tempora lookup, global, home, sql and the wand) on one "Tempora-Query" thread so a slow query never
// holds up ticks. Each lookup is two steps
// - a Query, run on the query thread. Gets no player or world, only plain values copied out before it was submitted,
// and reads the dbs through connection()
// - a Show, run on the server thread at the end of a tick, which sends the chat and render packets. 1.7.10 isn't safe
// to send those from another thread
// One lookup at a time per sender, every lookup has a time limit and the queue is bounded, so neither a slow query nor
// someone spamming the wand can pile up work
public final class TemporaQueries {

    // Work done on the query thread. Throwing fails the lookup and the sender gets told
    @FunctionalInterface
    public interface Query<T> {

        T run() throws Exception;
    }

    // Shows a query's result on the server thread, sender is still online
    @FunctionalInterface
    public interface Show<T> {

        void show(ICommandSender sender, T result);
    }

    // Thrown on the query thread once the lookup has gone past its time limit
    public static final class QueryTimedOutException extends RuntimeException {

        QueryTimedOutException() {
            super("Query took too long", null, false, false);
        }
    }

    private static final int MAX_QUEUED_QUERIES = 64;
    // How long before telling the sender it's still searching, so a slow lookup doesn't look ignored
    private static final long SEARCHING_NOTICE_MILLISECONDS = 300;
    // SQLite VM steps between time limit checks, cheap and often enough to stop within milliseconds
    private static final int PROGRESS_CHECK_INTERVAL = 10_000;
    private static final long STOP_TIMEOUT_MILLISECONDS = 5_000;

    private static final ArrayBlockingQueue<Job<?>> pending = new ArrayBlockingQueue<>(MAX_QUEUED_QUERIES);
    private static final ConcurrentLinkedQueue<Job<?>> finished = new ConcurrentLinkedQueue<>();
    // Sender -> their lookup that hasn't been shown yet, one at a time each
    private static final ConcurrentHashMap<String, Job<?>> inFlight = new ConcurrentHashMap<>();

    private static @Nullable Worker worker;
    // Worker on the current thread, so connection() can tell it's on the query thread
    private static final ThreadLocal<Worker> currentWorker = new ThreadLocal<>();

    private TemporaQueries() {}

    // Runs query on the query thread then show with its result on the server thread. Call from the server thread. Tells
    // the sender instead if their last lookup isn't done yet or too many are waiting
    public static <T> void submit(ICommandSender sender, Query<T> query, Show<T> show) {
        Job<T> job = new Job<>(sender, query, show);

        if (inFlight.putIfAbsent(job.senderKey, job) != null) {
            sendMessage(sender, EnumChatFormatting.GRAY, "tempora.query.already_running");
            return;
        }

        // Also replaces a dead worker, shouldn't happen since each job catches what it throws
        if (worker == null || !worker.thread.isAlive()) {
            worker = new Worker();
            worker.thread.start();
        }

        if (!pending.offer(job)) {
            inFlight.remove(job.senderKey, job);
            sendMessage(sender, EnumChatFormatting.RED, "tempora.query.server_busy");
        }
    }

    // Query thread's read only connection to a logger's db. Only callable from a Query so no lookup can run SQL on the
    // server thread by mistake
    public static Connection connection(GenericLogger<?> logger) throws SQLException {
        Worker current = currentWorker.get();
        if (current == null) {
            throw new IllegalStateException("Tempora databases are only read on the Tempora-Query thread.");
        }
        return current.connection(logger);
    }

    // Throws QueryTimedOutException if the running lookup is past its time limit. Call on the query thread after a
    // query fails, SQLite reports the abort as a normal error
    public static void throwIfTimedOut() {
        Worker current = currentWorker.get();
        if (current != null && current.pastDeadline()) throw new QueryTimedOutException();
    }

    // Shows finished lookups and tells senders whose lookup is slow that it's still going. Every server tick
    public static void onServerTick() {
        Job<?> job;
        while ((job = finished.poll()) != null) {
            inFlight.remove(job.senderKey, job);
            job.showOnServerThread();
        }

        long now = System.currentTimeMillis();
        for (Job<?> waiting : inFlight.values()) {
            if (!waiting.searchingNoticeSent && now - waiting.submittedAt >= SEARCHING_NOTICE_MILLISECONDS) {
                waiting.searchingNoticeSent = true;
                ICommandSender sender = waiting.findSender();
                if (sender != null) sendMessage(sender, EnumChatFormatting.GRAY, "tempora.query.searching");
            }
        }
    }

    // Stops the query thread, cancels the running query, drops the rest and closes its connections. Before the loggers
    // close their dbs
    public static void onServerClose() {
        if (worker != null) {
            worker.stop();
            worker = null;
        }

        pending.clear();
        finished.clear();
        inFlight.clear();
    }

    private static void sendMessage(ICommandSender sender, EnumChatFormatting colour, String key, Object... args) {
        IChatComponent message = new ChatComponentTranslation(key, args);
        message.getChatStyle()
            .setColor(colour);
        sender.addChatMessage(message);
    }

    // Query thread and what only it touches. New one per server so one that fails to stop in time can't share anything
    // with the next
    private static final class Worker {

        private final Thread thread = new Thread(this::run, "Tempora-Query");
        private volatile boolean stopping;
        // When the running lookup has to stop by, in System.nanoTime(). Query thread only
        private long deadlineNanos;
        // One read only connection per logger, opened on first use. Query thread only
        private final Map<GenericLogger<?>, Connection> connections = new HashMap<>();

        Worker() {
            // Only reads, nothing to finish before the JVM exits
            thread.setDaemon(true);
        }

        private void run() {
            currentWorker.set(this);
            try {
                while (!stopping) {
                    Job<?> job;
                    try {
                        job = pending.take();
                    } catch (InterruptedException e) {
                        // How stop() wakes this thread
                        continue;
                    }

                    deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(CommandsConfig.queryTimeLimitSeconds);
                    job.runOnQueryThread();
                    if (!stopping) finished.add(job);
                }
            } finally {
                closeConnections();
            }
        }

        boolean pastDeadline() {
            return System.nanoTime() > deadlineNanos;
        }

        Connection connection(GenericLogger<?> logger) throws SQLException {
            Connection conn = connections.get(logger);
            if (conn != null) return conn;

            conn = logger.getDatabaseManager()
                .getReadOnlyConnection();
            // SQLite calls this every so often while a query runs, on this thread. Non zero aborts the query
            ProgressHandler.setHandler(conn, PROGRESS_CHECK_INTERVAL, new ProgressHandler() {

                @Override
                protected int progress() {
                    return stopping || pastDeadline() ? 1 : 0;
                }
            });
            connections.put(logger, conn);
            return conn;
        }

        void stop() {
            stopping = true;
            // Wakes the thread if it's waiting for work, a running query notices `stopping` instead
            thread.interrupt();
            try {
                thread.join(STOP_TIMEOUT_MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
            if (thread.isAlive()) LOG.error("Tempora-Query did not stop in time, leaving it to finish on its own.");
        }

        private void closeConnections() {
            for (Map.Entry<GenericLogger<?>, Connection> entry : connections.entrySet()) {
                try {
                    entry.getValue()
                        .close();
                } catch (SQLException e) {
                    LOG.error(
                        "Could not close the lookup connection to {}.",
                        entry.getKey()
                            .getLoggerName(),
                        e);
                }
            }
            connections.clear();
        }
    }

    private static final class Job<T> {

        // Players get found again by UUID when the result is shown since they might've logged off or respawned (new
        // EntityPlayerMP) in the meantime. Other senders like the console are kept as is
        private final @Nullable UUID playerUUID;
        private final @Nullable ICommandSender otherSender;
        private final String senderKey;
        private final Query<T> query;
        private final Show<T> show;
        private final long submittedAt = System.currentTimeMillis();
        // Server thread only
        private boolean searchingNoticeSent;

        // Set on the query thread, read on the server thread after `finished` hands the job over
        private T result;
        private Throwable failure;

        Job(ICommandSender sender, Query<T> query, Show<T> show) {
            if (sender instanceof EntityPlayerMP player) {
                playerUUID = player.getUniqueID();
                otherSender = null;
                senderKey = playerUUID.toString();
            } else {
                playerUUID = null;
                otherSender = sender;
                senderKey = "sender:" + sender.getCommandSenderName();
            }
            this.query = query;
            this.show = show;
        }

        void runOnQueryThread() {
            try {
                result = query.run();
            } catch (Throwable t) {
                // Errors too, the sender is still waiting on this job and the thread has to live on for everyone else
                failure = t;
            }
        }

        void showOnServerThread() {
            ICommandSender sender = findSender();
            if (sender == null) return;

            if (failure instanceof QueryTimedOutException) {
                sendMessage(
                    sender,
                    EnumChatFormatting.RED,
                    "tempora.query.timed_out",
                    new ChatComponentNumber(CommandsConfig.queryTimeLimitSeconds));
                return;
            }
            if (failure != null) {
                LOG.error("A Tempora lookup failed.", failure);
                sendMessage(
                    sender,
                    EnumChatFormatting.RED,
                    "tempora.query.failed",
                    String.valueOf(failure.getMessage()));
                return;
            }

            try {
                show.show(sender, result);
            } catch (RuntimeException e) {
                // Can't escape into the server tick
                LOG.error("Could not show a Tempora lookup's results.", e);
                sendMessage(sender, EnumChatFormatting.RED, "tempora.query.failed", String.valueOf(e.getMessage()));
            }
        }

        @Nullable
        ICommandSender findSender() {
            if (playerUUID == null) return otherSender;

            for (Object o : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                if (playerUUID.equals(player.getUniqueID())) return player;
            }
            return null;
        }
    }
}
