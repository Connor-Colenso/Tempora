package com.colen.tempora.loggers.generic;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

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
     * SQL type (REAL, INTEGER, TEXT, etc.)
     */
    String type();

    /**
     * Extra constraints (NOT NULL, DEFAULT, etc.)
     */
    String constraints() default "";
}
