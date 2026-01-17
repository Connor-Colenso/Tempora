package com.colen.tempora.loggers.generic.column;

import java.lang.reflect.Field;

public final class ColumnAccessor {

    final public Field field;
    final public Binder binder;

    public ColumnAccessor(Field field, Binder binder) {
        this.field = field;
        this.binder = binder;
    }
}
