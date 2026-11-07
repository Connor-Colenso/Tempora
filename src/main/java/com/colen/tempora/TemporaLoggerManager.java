package com.colen.tempora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.colen.tempora.config.Config;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PlayerIDs;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    private static final Map<String, GenericLogger<?>> LOGGERS = new HashMap<>();

    // Keyed by logger name, which is also what the client gets sent, so both sides agree when the server has loggers
    // from other mods the client doesn't
    private static final Map<String, Supplier<? extends GenericEventInfo>> FACTORIES = new HashMap<>();
    private static final Set<Class<?>> EVENT_INFO_CLASSES = new HashSet<>();

    // Whether loadConfig has run, loggers registered after that read their config on register
    private static boolean configLoaded;

    /**
     * Adds a logger of either kind, {@link GenericPositionalLogger} for stuff at a place or {@link GenericGlobalLogger}
     * for server wide stuff. Call during mod loading (preInit, init or postInit) on both client and server. Anything
     * wrong with the logger (names, columns) gets thrown here instead of on server start
     * <p>
     * See TemporaEvents for examples
     */
    public static <EventInfo extends GenericEventInfo> void register(GenericLogger<EventInfo> logger,
        Supplier<EventInfo> factory) {

        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        String loggerName = logger.getLoggerName();

        if (GenericLogger.areLoggersStarted()) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + " was registered while the server is running. Register loggers while mods load (preInit, init or postInit).");
        }

        // Probe the factory
        EventInfo probe = Objects.requireNonNull(factory.get(), "factory.get() must not return null");

        // The event names its logger, that's how the client and commands find it, so they have to match
        if (!loggerName.equals(probe.getLoggerName())) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + " makes events that say they belong to "
                    + probe.getLoggerName()
                    + ". GenericEventInfo.getLoggerName() must return the same name as its logger.");
        }

        // Table columns come from newEventInfo() but the client builds events with the factory
        if (logger.newEventInfo()
            .getClass() != probe.getClass()) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + "'s newEventInfo() and the factory it was registered with make different classes.");
        }

        // Check the names. The full name is a db file, a table and a config category, and players type both in commands
        // where case is ignored
        checkName(loggerName, "name", loggerName);
        checkName(logger.getShortName(), "short name", loggerName);

        if (findLogger(loggerName) != null) {
            throw new IllegalStateException("Logger name already in use, ignoring case: " + loggerName);
        }

        if (findLogger(logger.getShortName()) != null) {
            throw new IllegalStateException("Logger short name already in use: " + logger.getShortName());
        }

        if (EVENT_INFO_CLASSES.contains(probe.getClass())) {
            throw new IllegalStateException(
                "EventInfo already registered: " + probe.getClass()
                    .getName());
        }

        // Check columns now so a field Tempora can't store fails here not on server start
        try {
            logger.checkColumns();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Logger " + loggerName + " has a column Tempora cannot store.", e);
        }

        LOGGERS.put(loggerName, logger);
        EVENT_INFO_CLASSES.add(probe.getClass());
        FACTORIES.put(loggerName, factory);

        // Registered after we read our config, e.g. by another mod in its own init
        if (configLoaded) {
            configure(logger);
            Config.saveLoggerConfigs();
        }
    }

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    // Names we use ourselves, the players db file and the word for every logger in /tempora region set
    private static final List<String> RESERVED_NAMES = Arrays.asList(PlayerIDs.TABLE, "all");

    private static void checkName(String name, String what, String loggerName) {
        if (name == null || !VALID_NAME.matcher(name)
            .matches()) {
            throw new IllegalStateException(
                "Logger " + loggerName
                    + "'s "
                    + what
                    + " must be letters, digits and underscores, starting with a letter: "
                    + name);
        }
        for (String reserved : RESERVED_NAMES) {
            if (reserved.equalsIgnoreCase(name)) {
                throw new IllegalStateException(
                    "Logger " + loggerName + "'s " + what + " is a name Tempora uses itself: " + name);
            }
        }
    }

    /** Reads every registered logger's settings, loggers registered after this read theirs on register */
    public static void loadConfig() {
        for (GenericLogger<?> logger : LOGGERS.values()) {
            configure(logger);
        }
        configLoaded = true;

        Config.saveLoggerConfigs();
    }

    private static void configure(GenericLogger<?> logger) {
        Configuration config = Config.loggerConfig(logger);
        logger.handleCustomLoggerConfig(config);
        logger.genericConfig(config);
    }

    // Blank event for the named logger, or null if this side doesn't have it
    public static @Nullable GenericEventInfo createEventInfo(String loggerName) {
        Supplier<? extends GenericEventInfo> factory = FACTORIES.get(loggerName);
        return factory == null ? null : factory.get();
    }

    public static @Nullable GenericLogger<?> getLogger(String loggerName) {
        return LOGGERS.get(loggerName);
    }

    // For names players type, the short name ("break") or full name ignoring case. Either kind of logger so commands
    // can say when the wrong kind was named
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

    // How a logger is shown to players, falls back to the stored name for loggers that don't exist any more
    public static String displayName(String loggerName) {
        GenericLogger<?> logger = LOGGERS.get(loggerName);
        return logger == null ? loggerName : logger.getShortName();
    }

    // Every logger, both kinds
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

    // Every logger's full name, which is also its table name
    @NotNull
    public static List<String> getAllLoggerNames() {
        return LOGGERS.values()
            .stream()
            .map(GenericLogger::getLoggerName)
            .collect(Collectors.toList());
    }

}
