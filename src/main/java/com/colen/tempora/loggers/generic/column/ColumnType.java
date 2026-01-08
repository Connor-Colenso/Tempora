package com.colen.tempora.loggers.generic.column;

import java.lang.reflect.Field;

public enum ColumnType {
    AUTO(null),

    BOOLEAN("INTEGER"),
    INTEGER("INTEGER"),
    LONG("INTEGER"),
    FLOAT("REAL"),
    DOUBLE("REAL"),
    TEXT("TEXT");

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
            "Cannot infer ColumnType for field " + field.getName() +
                " of type " + t.getName()
        );
    }
}
