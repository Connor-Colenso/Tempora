package com.colen.tempora;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    private static final Map<String, GenericPositionalLogger<?>> LOGGERS = new HashMap<>();

    // You must never change the loggerName once it is set, as this is what the SQL table is also named.
    public static void register(String loggerName, GenericPositionalLogger<?> logger) {
        logger.setName(loggerName);

        if (LOGGERS.containsKey(loggerName)) {
            throw new IllegalStateException("Logger already registered: " + loggerName);
        }

        LOGGERS.put(logger.getLoggerName(), logger);
    }

    public static GenericPositionalLogger<?> getLogger(String loggerName) {
        return LOGGERS.get(loggerName);
    }

    public static Collection<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(LOGGERS.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends GenericQueueElement>
    GenericPositionalLogger<T> getTypedLogger(String loggerName) {
        return (GenericPositionalLogger<T>) LOGGERS.get(loggerName);
    }

    @NotNull
    public static List<String> getAllLoggerNames() {

        return TemporaLoggerManager.getLoggerList()
            .stream()
            .map(GenericPositionalLogger::getLoggerName)
            .collect(Collectors.toList());
    }
}
