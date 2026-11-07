package com.colen.tempora.loggers.generic.column;

import java.lang.reflect.Field;

public enum ColumnType {

    AUTO(null),

    BOOLEAN("INTEGER"),
    INTEGER("INTEGER"),
    LONG("INTEGER"),
    FLOAT("REAL"),
    DOUBLE("REAL"),
    TEXT("TEXT"),

    /**
     * Player UUID on a String field, stored as the player's number from
     * {@link com.colen.tempora.loggers.generic.PlayerIDs} instead of text. Field starts as
     * {@link com.colen.tempora.utils.PlayerUtils#UNKNOWN_PLAYER_NAME} and reads back as the UUID. Never inferred so
     * player columns have to say so. Not for mob UUIDs, see PlayerIDs
     */
    PLAYER("INTEGER"),

    /**
     * NBT as {@link com.colen.tempora.utils.nbt.NBTUtils} encodes it, on a String field, stored as the compressed bytes
     * not the Base64 text (a quarter smaller). Markers like NO_NBT are stored as is. Never inferred so NBT columns have
     * to say so
     */
    NBT("BLOB");

    private final String sqliteType;

    ColumnType(String sqliteType) {
        this.sqliteType = sqliteType;
    }

    public String getSqlType() {
        if (this == AUTO) {
            throw new IllegalStateException("AUTO has no SQL type");
        }
        return sqliteType;
    }

    public static ColumnType inferFrom(Field field) {
        Class<?> t = field.getType();

        if (t == boolean.class || t == Boolean.class) return BOOLEAN;
        if (t == int.class || t == Integer.class) return INTEGER;
        if (t == long.class || t == Long.class) return LONG;
        if (t == float.class || t == Float.class) return FLOAT;
        if (t == double.class || t == Double.class) return DOUBLE;
        if (t == String.class) return TEXT;

        throw new IllegalStateException(
            "Cannot infer ColumnType for field " + field.getName() + " of type " + t.getName());
    }
}
