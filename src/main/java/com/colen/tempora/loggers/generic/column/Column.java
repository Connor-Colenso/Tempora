package com.colen.tempora.loggers.generic.column;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of an event as a database column. Every column is NOT NULL, with the field's value in a new event as
 * its default: the value events stored before the column existed read back as.
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
     * Previous column name(s) this field was known as, oldest first.
     * When present, table initialisation renames the existing column in place
     * (preserving its data) instead of adding a new, empty column under the new name.
     */
    String[] renamedFrom() default {};

}
