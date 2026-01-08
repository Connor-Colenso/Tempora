package com.colen.tempora.loggers.generic.column;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field with its column order for database inserts.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /**
     * Database column name.
     * Defaults to field name.
     */
    String name() default "";

    /**
     * Type of the field.
     * <p>
     * This is mapped internally to an SQLite-compatible column type and
     * controls how values are written to and read from the database
     * (e.g. BOOLEAN is stored as INTEGER).
     */
    ColumnType type() default ColumnType.AUTO;

    /**
     * Extra constraints (NOT NULL, DEFAULT, etc.)
     */
    String constraints() default "";

}
