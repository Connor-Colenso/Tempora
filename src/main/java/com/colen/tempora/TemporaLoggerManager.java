package com.colen.tempora;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.colen.tempora.loggers.generic.column.Binder;
import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnAccessor;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    /* ---------------- LOGGER REGISTRY ---------------- */

    private static final Map<String, GenericPositionalLogger<?>> LOGGERS = new HashMap<>();

    /* ---------------- REGISTRY ---------------- */

    private static final Map<Integer, Supplier<? extends GenericEventInfo>> FACTORIES = new HashMap<>();
    private static final Map<Class<?>, Integer> CLASS_TO_ID = new HashMap<>();
    private static int nextEventInfoId = 0;

    /* ---------------- PACKET REGISTRATION ---------------- */

    // See TemporaEvents.java in Tempora mod for example usages.
    public static <EventInfo extends GenericEventInfo> void register(GenericPositionalLogger<EventInfo> logger,
        Supplier<EventInfo> factory) {

        // 1. Validate inputs
        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(factory, "factory must not be null");

        // 2. Probe factory output
        EventInfo probe = Objects.requireNonNull(factory.get(), "factory.get() must not return null");

        String loggerName = probe.getLoggerName();

        @SuppressWarnings("unchecked")
        Class<EventInfo> eventInfoClass = (Class<EventInfo>) probe.getClass();

        // 3. Validate uniqueness
        if (LOGGERS.containsKey(loggerName)) {
            throw new IllegalStateException("Logger already registered: " + loggerName);
        }

        if (CLASS_TO_ID.containsKey(eventInfoClass)) {
            throw new IllegalStateException("EventInfo already registered: " + eventInfoClass.getName());
        }

        // 4. Assign ID
        int id = nextEventInfoId++;

        // 5. Register
        LOGGERS.put(loggerName, logger);
        CLASS_TO_ID.put(eventInfoClass, id);
        FACTORIES.put(id, factory);
    }

    /* ---------------- NETWORK HELPERS ---------------- */

    public static int getEventInfoId(GenericEventInfo element) {
        Integer id = CLASS_TO_ID.get(element.getClass());
        if (id == null) {
            throw new IllegalStateException(
                "Unregistered EventInfo: " + element.getClass()
                    .getName());
        }
        return id;
    }

    public static @NotNull GenericEventInfo createEventInfo(int id) {
        Supplier<? extends GenericEventInfo> f = FACTORIES.get(id);
        if (f == null) {
            throw new IllegalStateException("Unknown EventInfo id: " + id);
        }
        return f.get();
    }

    /* ---------------- LOGGER ACCESS ---------------- */

    public static @NotNull GenericPositionalLogger<?> getLogger(String loggerName) {
        GenericPositionalLogger<?> logger = LOGGERS.get(loggerName);
        if (logger == null) {
            throw new IllegalStateException("Unknown logger name: " + loggerName);
        } else {
            return logger;
        }
    }

    public static Collection<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(LOGGERS.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends GenericEventInfo> GenericPositionalLogger<T> getTypedLogger(String loggerName) {
        return (GenericPositionalLogger<T>) LOGGERS.get(loggerName);
    }

    @NotNull
    public static List<String> getAllLoggerNames() {
        return LOGGERS.values()
            .stream()
            .map(GenericPositionalLogger::getLoggerName)
            .collect(Collectors.toList());
    }

    // Reflection assistance.

    private static final ConcurrentHashMap<String, List<ColumnAccessor>> NAME_TO_ACCESSORS_CACHE = new ConcurrentHashMap<>();

    public static List<ColumnAccessor> getColumnAccessors(GenericPositionalLogger<? extends GenericEventInfo> logger) {

        List<ColumnAccessor> cached = NAME_TO_ACCESSORS_CACHE.get(logger.getLoggerName());
        if (cached != null) return cached;

        List<ColumnAccessor> accessors = new ArrayList<>();

        for (Field field : getColumnFieldsAlphabetically(logger)) {
            Class<?> type = field.getType();

            final Binder binder;
            if (type == int.class || type == Integer.class) {
                binder = (ps, i, v) -> ps.setInt(i, (Integer) v);
            } else if (type == long.class || type == Long.class) {
                binder = (ps, i, v) -> ps.setLong(i, (Long) v);
            } else if (type == double.class || type == Double.class) {
                binder = (ps, i, v) -> ps.setDouble(i, (Double) v);
            } else if (type == float.class || type == Float.class) {
                binder = (ps, i, v) -> ps.setFloat(i, (Float) v);
            } else if (type == boolean.class || type == Boolean.class) {
                binder = (ps, i, v) -> ps.setInt(i, ((Boolean) v) ? 1 : 0);
            } else if (type == String.class) {
                binder = (ps, i, v) -> ps.setString(i, (String) v);
            } else {
                throw new IllegalStateException("Unsupported field type: " + type);
            }

            accessors.add(new ColumnAccessor(field, binder));
        }

        NAME_TO_ACCESSORS_CACHE.put(logger.getLoggerName(), accessors);
        return accessors;
    }

    public static List<Field> getColumnFieldsAlphabetically(
        GenericPositionalLogger<? extends GenericEventInfo> logger) {

        List<Class<?>> hierarchy = new ArrayList<>();

        for (Class<?> c = logger.newEventInfo().getClass();
             c != null && c != Object.class;
             c = c.getSuperclass()) {
            hierarchy.add(c);
        }

        Collections.reverse(hierarchy);

        List<Field> result = new ArrayList<>();

        for (Class<?> cls : hierarchy) {
            Arrays.stream(cls.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Column.class))
                .peek(f -> f.setAccessible(true))
                .sorted(Comparator.comparing(Field::getName))
                .forEach(result::add);
        }

        return result;
    }

}
