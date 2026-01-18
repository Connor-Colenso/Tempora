package com.colen.tempora.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.column.ColumnType;

public class ReflectionUtils {

    private static final ConcurrentHashMap<String, List<ColumnDef>> CACHED_COLUMN_DEFS_MAP = new ConcurrentHashMap<>();

    public static List<ColumnDef> getAllTableColumns(GenericPositionalLogger<?> genericPositionalLogger) {

        // Cache hit
        List<ColumnDef> cached = CACHED_COLUMN_DEFS_MAP.get(genericPositionalLogger.getLoggerName());
        if (cached != null) {
            return cached;
        }

        List<ColumnDef> defs = new ArrayList<>();

        for (Field field : getColumnFieldsAlphabetically(genericPositionalLogger)) {

            Column col = field.getAnnotation(Column.class);

            String name = col.name()
                .isEmpty() ? field.getName() : col.name();

            ColumnType type = col.type() == ColumnType.AUTO ? ColumnType.inferFrom(field) : col.type();

            ColumnDef.ColumnAccessor accessor = buildColumnAccessor(field);

            defs.add(new ColumnDef(name, type.getSqlType(), col.constraints(), accessor));
        }

        // Freeze before caching
        List<ColumnDef> immutable = Collections.unmodifiableList(defs);
        CACHED_COLUMN_DEFS_MAP.put(genericPositionalLogger.getLoggerName(), immutable);

        return immutable;
    }

    private static ColumnDef.ColumnAccessor buildColumnAccessor(Field field) {

        field.setAccessible(true);

        final MethodHandle getter;
        final MethodHandle setter;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            getter = lookup.unreflectGetter(field);
            setter = lookup.unreflectSetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to create MethodHandles for " + field, e);
        }

        final Class<?> type = field.getType();
        final ColumnDef.ColumnAccessor.Binder binder;
        final ColumnDef.ColumnAccessor.Reader reader;

        if (type == int.class || type == Integer.class) {
            binder = (ps, i, v) -> ps.setInt(i, (Integer) v);
            reader = ResultSet::getInt;
        } else if (type == long.class || type == Long.class) {
            binder = (ps, i, v) -> ps.setLong(i, (Long) v);
            reader = ResultSet::getLong;
        } else if (type == double.class || type == Double.class) {
            binder = (ps, i, v) -> ps.setDouble(i, (Double) v);
            reader = ResultSet::getDouble;
        } else if (type == float.class || type == Float.class) {
            binder = (ps, i, v) -> ps.setFloat(i, (Float) v);
            reader = ResultSet::getFloat;
        } else if (type == boolean.class || type == Boolean.class) {
            binder = (ps, i, v) -> ps.setInt(i, ((Boolean) v) ? 1 : 0);
            reader = (rs, name) -> rs.getInt(name) != 0;
        } else if (type == String.class) {
            binder = (ps, i, v) -> ps.setString(i, (String) v);
            reader = ResultSet::getString;
        } else {
            throw new IllegalStateException("Unsupported field type: " + type.getName());
        }

        return new ColumnDef.ColumnAccessor(getter, setter, binder, reader);
    }

    // Helpers.

    private static List<Field> getColumnFieldsAlphabetically(
        GenericPositionalLogger<? extends GenericEventInfo> logger) {

        List<Class<?>> hierarchy = new ArrayList<>();

        for (Class<?> c = logger.newEventInfo()
            .getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
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

    public static double squaredDistance(GenericEventInfo e, double x, double y, double z) {
        double dx = e.x - x;
        double dy = e.y - y;
        double dz = e.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
