package com.colen.tempora.loggers.generic.column;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ColumnAccessor {

    final public Field field;
    final public Binder binder;

    public ColumnAccessor(Field field, Binder binder) {
        this.field = field;
        this.binder = binder;
    }

    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement ps, int index, Object value) throws SQLException;
    }

}
