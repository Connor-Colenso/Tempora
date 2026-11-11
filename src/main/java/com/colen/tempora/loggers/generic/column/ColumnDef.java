package com.colen.tempora.loggers.generic.column;

import java.lang.invoke.MethodHandle;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.colen.tempora.loggers.generic.column.names.NameRole;

public class ColumnDef {

    public final String name;
    public final ColumnType columnType;
    public final String type; // SQL type of columnType
    public final String extraCondition;
    public final List<String> renamedFrom;
    public final ColumnAccessor columnAccessor;
    // For f:, see Column.nameRole. NameRole.class for none
    public final Class<? extends NameRole> nameRole;
    public final String part;
    public final String group;

    public ColumnDef(String name, ColumnType columnType, String extraCondition, List<String> renamedFrom,
        ColumnAccessor columnAccessor, Class<? extends NameRole> nameRole, String part, String group) {
        this.name = name;
        this.columnType = columnType;
        this.type = columnType.getSqlType();
        this.extraCondition = extraCondition;
        this.renamedFrom = renamedFrom;
        this.columnAccessor = columnAccessor;
        this.nameRole = nameRole;
        this.part = part;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    // INTEGER PRIMARY KEY is the rowid, SQLite fills it in on insert
    public boolean isAssignedByDatabase() {
        return "INTEGER".equals(type) && "PRIMARY KEY".equalsIgnoreCase(extraCondition);
    }

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
