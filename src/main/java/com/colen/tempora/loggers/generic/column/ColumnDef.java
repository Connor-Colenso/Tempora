package com.colen.tempora.loggers.generic.column;

import java.lang.invoke.MethodHandle;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ColumnDef {

    public final String name;
    public final String type;
    public final String extraCondition;
    public final List<String> renamedFrom;
    public final ColumnAccessor columnAccessor;

    public ColumnDef(String name, String type, String extraCondition, List<String> renamedFrom,
        ColumnAccessor columnAccessor) {
        this.name = name;
        this.type = type;
        this.extraCondition = extraCondition;
        this.renamedFrom = renamedFrom;
        this.columnAccessor = columnAccessor;
    }

    public String getName() {
        return name;
    }

    // An INTEGER PRIMARY KEY is the table's rowid, which SQLite fills in itself on insert.
    public boolean isAssignedByDatabase() {
        return "INTEGER".equals(type) && "PRIMARY KEY".equalsIgnoreCase(extraCondition);
    }

    // --------------------------------------
    // Accessor (read + write)
    // --------------------------------------
    public static final class ColumnAccessor {

        private final MethodHandle getter;
        private final MethodHandle setter;
        public final Binder binder;
        public final Reader reader;

        public ColumnAccessor(MethodHandle getter, MethodHandle setter, Binder binder, Reader reader) {
            this.getter = getter;
            this.setter = setter;
            this.binder = binder;
            this.reader = reader;
        }

        public Object get(Object instance) {
            try {
                return getter.invoke(instance);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to read column value", t);
            }
        }

        public void set(Object instance, Object value) {
            try {
                setter.invoke(instance, value);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to write column value", t);
            }
        }

        @FunctionalInterface
        public interface Binder {

            void bind(PreparedStatement ps, int index, Object value) throws SQLException;
        }

        @FunctionalInterface
        public interface Reader {

            Object read(ResultSet rs, String columnName) throws SQLException;
        }
    }

}
