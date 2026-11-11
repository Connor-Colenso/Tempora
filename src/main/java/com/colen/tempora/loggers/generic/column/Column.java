package com.colen.tempora.loggers.generic.column;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.lookups.ItemNames;

/**
 * Marks an event field as a db column. Every column is NOT NULL and defaults to the field's value in a new event, which
 * is what events stored before the column existed read back as
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /** Column name, defaults to the field name */
    String name() default "";

    /**
     * How the field is stored, mapped to an SQLite type that controls how values are written and read (e.g. BOOLEAN is
     * stored as INTEGER)
     */
    ColumnType type() default ColumnType.AUTO;

    /**
     * Old names this field had, oldest first. If present the existing column gets renamed in place and keeps its data,
     * instead of adding a new empty one
     */
    String[] renamedFrom() default {};

    /**
     * The kind of thing this column names for the f: filter of /tempora lookup, e.g. {@link ItemNames}. Most columns
     * have none, which is NameRole itself
     */
    Class<? extends NameRole> nameRole() default NameRole.class;

    /**
     * Which {@link NamePart} of its {@link #nameRole} this column is, e.g. {@link ItemNames#META}. Can be left out when
     * the role has only one part
     */
    String part() default "";

    /**
     * Tells apart the columns of two things of the same role in one event. Block change has an item "before" and one
     * "after". Leave it empty when there's only one
     */
    String group() default "";

}
