package com.colen.tempora.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.PlayerIDs;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.nbt.NBTUtils;

public class ReflectionUtils {

    private static final ConcurrentHashMap<String, List<ColumnDef>> CACHED_COLUMN_DEFS_MAP = new ConcurrentHashMap<>();

    public static List<ColumnDef> getAllTableColumns(GenericLogger<?> logger) {

        List<ColumnDef> cached = CACHED_COLUMN_DEFS_MAP.get(logger.getLoggerName());
        if (cached != null) {
            return cached;
        }

        List<ColumnDef> defs = new ArrayList<>();
        // New event, each field's value in it is that column's default
        GenericEventInfo fresh = logger.newEventInfo();

        for (Field field : getColumnFieldsAlphabetically(logger)) {

            Column col = field.getAnnotation(Column.class);

            String name = col.name()
                .isEmpty() ? field.getName() : col.name();

            ColumnType type = col.type() == ColumnType.AUTO ? ColumnType.inferFrom(field) : col.type();

            ColumnDef.ColumnAccessor accessor = buildColumnAccessor(field, type);

            String constraints;
            if (isEventID(field)) {
                // The rowid, SQLite fills it in so no default
                constraints = "PRIMARY KEY";
            } else if (type == ColumnType.PLAYER) {
                constraints = withDefault(playerDefault(field, accessor.get(fresh)));
            } else {
                constraints = withDefault(sqlLiteral(field, accessor.get(fresh)));
            }

            defs.add(new ColumnDef(name, type, constraints, Arrays.asList(col.renamedFrom()), accessor));
        }

        List<ColumnDef> immutable = Collections.unmodifiableList(defs);
        CACHED_COLUMN_DEFS_MAP.put(logger.getLoggerName(), immutable);

        return immutable;
    }

    private static ColumnDef.ColumnAccessor buildColumnAccessor(Field field, ColumnType columnType) {

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

        if (columnType == ColumnType.PLAYER) {
            if (type != String.class) {
                throw new IllegalStateException(
                    field.getDeclaringClass()
                        .getSimpleName() + "."
                        + field.getName()
                        + " is a PLAYER column, so it must be a String.");
            }
            // Null as the unknown player instead of failing the batch the event goes out with
            binder = (ps, i, v) -> ps.setInt(i, v == null ? PlayerIDs.UNKNOWN_ID : PlayerIDs.idFor((String) v));
            reader = (rs, name) -> PlayerIDs.uuidFor(rs.getInt(name));
        } else if (columnType == ColumnType.NBT) {
            if (type != String.class) {
                throw new IllegalStateException(
                    field.getDeclaringClass()
                        .getSimpleName() + "."
                        + field.getName()
                        + " is an NBT column, so it must be a String.");
            }
            binder = (ps, i, v) -> bindNBT(ps, i, (String) v);
            reader = (rs, name) -> {
                Object stored = rs.getObject(name);
                if (stored instanceof byte[]bytes) return Base64.getEncoder()
                    .encodeToString(bytes);
                return stored == null ? NBTUtils.NO_NBT : stored.toString();
            };
        } else if (type == int.class || type == Integer.class) {
            binder = (ps, i, v) -> ps.setInt(i, (Integer) v);
            reader = ResultSet::getInt;
        } else if (type == long.class || type == Long.class) {
            binder = (ps, i, v) -> ps.setLong(i, (Long) v);
            reader = ResultSet::getLong;
        } else if (type == double.class || type == Double.class) {
            // NaN binds as NULL and every column is NOT NULL, so a mod's broken rotation (BoP pixies...) is stored
            // as 0 instead of losing the event. Positions never get here as NaN, logEvent turns those away
            binder = (ps, i, v) -> ps.setDouble(i, Double.isNaN((Double) v) ? 0 : (Double) v);
            reader = ResultSet::getDouble;
        } else if (type == float.class || type == Float.class) {
            binder = (ps, i, v) -> ps.setFloat(i, Float.isNaN((Float) v) ? 0 : (Float) v);
            reader = ResultSet::getFloat;
        } else if (type == boolean.class || type == Boolean.class) {
            binder = (ps, i, v) -> ps.setInt(i, ((Boolean) v) ? 1 : 0);
            reader = (rs, name) -> rs.getInt(name) != 0;
        } else if (type == String.class) {
            // Every column is NOT NULL so a null (a mod naming its inventory null say) is stored empty
            binder = (ps, i, v) -> ps.setString(i, v == null ? "" : (String) v);
            reader = ResultSet::getString;
        } else {
            throw new IllegalStateException("Unsupported field type: " + type.getName());
        }

        return new ColumnDef.ColumnAccessor(getter, setter, binder, reader);
    }

    // Real NBT as bytes, a marker as text. Anything that isn't Base64 (never made by NBTUtils) is stored as unreadable
    // instead of failing the batch
    private static void bindNBT(PreparedStatement ps, int i, String encodedNBT) throws SQLException {
        if (!NBTUtils.hasStoredNBT(encodedNBT)) {
            ps.setString(i, encodedNBT == null ? NBTUtils.NO_NBT : encodedNBT);
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder()
                .decode(encodedNBT);
        } catch (IllegalArgumentException e) {
            ps.setString(i, NBTUtils.NBT_UNREADABLE);
            return;
        }
        ps.setBytes(i, bytes);
    }

    private static boolean isEventID(Field field) {
        return field.getDeclaringClass() == GenericEventInfo.class && field.getName()
            .equals("eventID");
    }

    // Every column is NOT NULL with its field's value in a new event as DEFAULT. When a later version adds a column,
    // events already stored get that default instead of NULL, which the event code would then have to cope with
    private static String withDefault(String defaultValue) {
        return "NOT NULL DEFAULT " + defaultValue;
    }

    // Field value as an SQL literal. Null is refused, every column needs a real default
    private static String sqlLiteral(Field field, Object value) {
        if (value == null) {
            throw new IllegalStateException(
                field.getDeclaringClass()
                    .getSimpleName() + "."
                    + field.getName()
                    + " is null in a new event. Give every @Column field a non-null initial value, e.g. = \"\" for a"
                    + " String: it is the value events logged before the column existed read back as.");
        }
        if (value instanceof String s) return "'" + s.replace("'", "''") + "'";
        if (value instanceof Boolean b) return b ? "1" : "0";
        if ((value instanceof Double d && !Double.isFinite(d)) || (value instanceof Float f && !Float.isFinite(f))) {
            throw new IllegalStateException(
                field.getDeclaringClass()
                    .getSimpleName() + "."
                    + field.getName()
                    + " starts as "
                    + value
                    + ", which SQLite can't store.");
        }
        return value.toString();
    }

    // A player column's default is the unknown player, whose number is the same in every world. Any other start value
    // would need a number from a world's Players.db, which isn't open when loggers register
    private static String playerDefault(Field field, Object value) {
        if (!PlayerUtils.UNKNOWN_PLAYER_NAME.equals(value)) {
            throw new IllegalStateException(
                field.getDeclaringClass()
                    .getSimpleName() + "."
                    + field.getName()
                    + " is a PLAYER column, so it must start as PlayerUtils.UNKNOWN_PLAYER_NAME, not "
                    + value
                    + ".");
        }
        return String.valueOf(PlayerIDs.UNKNOWN_ID);
    }

    private static List<Field> getColumnFieldsAlphabetically(GenericLogger<?> logger) {

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
}
