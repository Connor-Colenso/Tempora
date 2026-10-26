package com.colen.tempora;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    /* ---------------- LOGGER REGISTRY ---------------- */

    private static final Map<String, GenericLogger<?>> LOGGERS = new HashMap<>();

    /* ---------------- REGISTRY ---------------- */

    // Keyed by logger name, which is also what the client is sent, so both sides agree even when the server has
    // loggers (from other mods) that the client does not.
    private static final Map<String, Supplier<? extends GenericEventInfo>> FACTORIES = new HashMap<>();
    private static final Set<Class<?>> EVENT_INFO_CLASSES = new HashSet<>();

    // Whether loadConfig has run. Loggers registered after that read their config as they register.
    private static boolean configLoaded;

    /* ---------------- PACKET REGISTRATION ---------------- */

    /**
     * Adds a logger, either kind: a {@link GenericPositionalLogger} for events at a place, or a
     * {@link GenericGlobalLogger} for events that belong to the whole server. Call this while mods load (preInit, init
     * or postInit), on both client and server. Anything wrong with the logger (names, columns) is reported here, rather
     * than when the server starts.
     * <p>
     * See TemporaEvents.java in Tempora mod for example usages.
     */
    public static <EventInfo extends GenericEventInfo> void register(GenericLogger<EventInfo> logger,
        Supplier<EventInfo> factory) {

        // 1. Validate inputs
        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        String loggerName = logger.getLoggerName();

        if (GenericLogger.areLoggersStarted()) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + " was registered while the server is running. Register loggers while mods load (preInit, init or postInit).");
        }

        // 2. Probe factory output
        EventInfo probe = Objects.requireNonNull(factory.get(), "factory.get() must not return null");

        // The event names its logger, which is how the client and the commands find it, so the two must agree.
        if (!loggerName.equals(probe.getLoggerName())) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + " makes events that say they belong to "
                    + probe.getLoggerName()
                    + ". GenericEventInfo.getLoggerName() must return the same name as its logger.");
        }

        // The table's columns come from newEventInfo(), the client builds events with the factory.
        if (logger.newEventInfo()
            .getClass() != probe.getClass()) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + "'s newEventInfo() and the factory it was registered with make different classes.");
        }

        // 3. Validate uniqueness
        if (LOGGERS.containsKey(loggerName)) {
            throw new IllegalStateException("Logger already registered: " + loggerName);
        }

        if (findLogger(logger.getShortName()) != null) {
            throw new IllegalStateException("Logger short name already in use: " + logger.getShortName());
        }

        if (EVENT_INFO_CLASSES.contains(probe.getClass())) {
            throw new IllegalStateException(
                "EventInfo already registered: " + probe.getClass()
                    .getName());
        }

        // 4. Check the columns now, so a field Tempora cannot store fails here rather than when the server starts.
        try {
            logger.checkColumns();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Logger " + loggerName + " has a column Tempora cannot store.", e);
        }

        // 5. Register
        LOGGERS.put(loggerName, logger);
        EVENT_INFO_CLASSES.add(probe.getClass());
        FACTORIES.put(loggerName, factory);

        // Registered after Tempora read its config, e.g. by another mod in its own init.
        if (configLoaded) {
            configure(logger, Tempora.loggerConfig);
            if (Tempora.loggerConfig.hasChanged()) Tempora.loggerConfig.save();
        }
    }

    /** Reads every registered logger's settings. Loggers registered afterwards read theirs as they register. */
    public static void loadConfig(Configuration config) {
        for (GenericLogger<?> logger : LOGGERS.values()) {
            configure(logger, config);
        }
        configLoaded = true;

        if (config.hasChanged()) config.save();
    }

    private static void configure(GenericLogger<?> logger, Configuration config) {
        logger.handleCustomLoggerConfig(config);
        logger.genericConfig(config);
    }

    /* ---------------- NETWORK HELPERS ---------------- */

    /** A blank event for the named logger, or null if this side has no such logger. */
    public static @Nullable GenericEventInfo createEventInfo(String loggerName) {
        Supplier<? extends GenericEventInfo> factory = FACTORIES.get(loggerName);
        return factory == null ? null : factory.get();
    }

    /* ---------------- LOGGER ACCESS ---------------- */

    public static @Nullable GenericLogger<?> getLogger(String loggerName) {
        return LOGGERS.get(loggerName);
    }

    // For names players type: the short name ("break") or the full logger name, ignoring case. Either kind of logger,
    // so commands can tell the player when they named the wrong kind.
    public static @Nullable GenericLogger<?> findLogger(String typed) {
        for (GenericLogger<?> logger : LOGGERS.values()) {
            if (logger.getShortName()
                .equalsIgnoreCase(typed)
                || logger.getLoggerName()
                    .equalsIgnoreCase(typed)) {
                return logger;
            }
        }
        return null;
    }

    // How a logger is shown to players. Falls back to the stored name for loggers that no longer exist.
    public static String displayName(String loggerName) {
        GenericLogger<?> logger = LOGGERS.get(loggerName);
        return logger == null ? loggerName : logger.getShortName();
    }

    /** Every logger, of both kinds. */
    public static Collection<GenericLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(LOGGERS.values());
    }

    public static List<GenericPositionalLogger<?>> getPositionalLoggers() {
        List<GenericPositionalLogger<?>> loggers = new ArrayList<>();
        for (GenericLogger<?> logger : LOGGERS.values()) {
            if (logger instanceof GenericPositionalLogger<?>positional) loggers.add(positional);
        }
        return loggers;
    }

    public static List<GenericGlobalLogger<?>> getGlobalLoggers() {
        List<GenericGlobalLogger<?>> loggers = new ArrayList<>();
        for (GenericLogger<?> logger : LOGGERS.values()) {
            if (logger instanceof GenericGlobalLogger<?>global) loggers.add(global);
        }
        return loggers;
    }

    public static List<String> getPositionalShortNames() {
        return shortNames(getPositionalLoggers());
    }

    public static List<String> getGlobalShortNames() {
        return shortNames(getGlobalLoggers());
    }

    private static List<String> shortNames(Collection<? extends GenericLogger<?>> loggers) {
        return loggers.stream()
            .map(GenericLogger::getShortName)
            .sorted()
            .collect(Collectors.toList());
    }

    /** Every logger's full name, which is also its table name. */
    @NotNull
    public static List<String> getAllLoggerNames() {
        return LOGGERS.values()
            .stream()
            .map(GenericLogger::getLoggerName)
            .collect(Collectors.toList());
    }

}
