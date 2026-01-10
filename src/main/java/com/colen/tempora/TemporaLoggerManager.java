package com.colen.tempora;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    /* ---------------- LOGGER REGISTRY ---------------- */

    private static final Map<String, GenericPositionalLogger<?>> LOGGERS = new HashMap<>();

    /* ---------------- REGISTRY ---------------- */

    private static final Map<Integer, Supplier<? extends GenericQueueElement>> FACTORIES = new HashMap<>();
    private static final Map<Class<?>, Integer> CLASS_TO_ID = new HashMap<>();
    private static int nextQueueElementId = 0;

    /* ---------------- PACKET REGISTRATION ---------------- */

    // See TemporaEvents.java in Tempora mod for example usages.
    public static <EventToLog extends GenericQueueElement> void register(GenericPositionalLogger<EventToLog> logger,
        Supplier<EventToLog> factory) {

        // 1. Validate inputs
        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        // 2. Probe factory output
        EventToLog probe = Objects.requireNonNull(factory.get(), "factory.get() must not return null");

        String loggerName = probe.getLoggerName();

        @SuppressWarnings("unchecked")
        Class<EventToLog> queueElementClass = (Class<EventToLog>) probe.getClass();

        // 3. Validate uniqueness
        if (LOGGERS.containsKey(loggerName)) {
            throw new IllegalStateException("Logger already registered: " + loggerName);
        }

        if (CLASS_TO_ID.containsKey(queueElementClass)) {
            throw new IllegalStateException("QueueElement already registered: " + queueElementClass.getName());
        }

        // 4. Assign ID
        int id = nextQueueElementId++;

        // 5. Register
        LOGGERS.put(loggerName, logger);
        CLASS_TO_ID.put(queueElementClass, id);
        FACTORIES.put(id, factory);
    }

    /* ---------------- NETWORK HELPERS ---------------- */

    public static int getQueueElementId(GenericQueueElement element) {
        Integer id = CLASS_TO_ID.get(element.getClass());
        if (id == null) {
            throw new IllegalStateException(
                "Unregistered QueueElement: " + element.getClass()
                    .getName());
        }
        return id;
    }

    public static @NotNull GenericQueueElement createQueueElement(int id) {
        Supplier<? extends GenericQueueElement> f = FACTORIES.get(id);
        if (f == null) {
            throw new IllegalStateException("Unknown QueueElement id: " + id);
        }
        return f.get();
    }

    /* ---------------- LOGGER ACCESS ---------------- */

    public static GenericPositionalLogger<?> getLogger(String loggerName) {
        return LOGGERS.get(loggerName);
    }

    public static Collection<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(LOGGERS.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends GenericQueueElement> GenericPositionalLogger<T> getTypedLogger(String loggerName) {
        return (GenericPositionalLogger<T>) LOGGERS.get(loggerName);
    }

    @NotNull
    public static List<String> getAllLoggerNames() {
        return LOGGERS.values()
            .stream()
            .map(GenericPositionalLogger::getLoggerName)
            .collect(Collectors.toList());
    }
}
