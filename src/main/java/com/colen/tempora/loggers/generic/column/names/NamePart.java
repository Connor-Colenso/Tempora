package com.colen.tempora.loggers.generic.column.names;

import com.colen.tempora.loggers.generic.column.ColumnType;

/** One column of a {@link NameRole}, e.g. an item's ID. Matched as a number or as text */
public final class NamePart {

    /** A value meaning any value of that part fits, e.g. the meta of an item without subtypes */
    public static final Object ANY = new Object() {

        @Override
        public String toString() {
            return "ANY";
        }
    };

    public final String name;
    /** {@link ColumnType#INTEGER} or {@link ColumnType#TEXT}, what the column has to be */
    public final ColumnType type;

    private NamePart(String name, ColumnType type) {
        this.name = name;
        this.type = type;
    }

    /** On an int field, values are Integers */
    public static NamePart integer(String name) {
        return new NamePart(name, ColumnType.INTEGER);
    }

    /** On a String field, values are Strings */
    public static NamePart text(String name) {
        return new NamePart(name, ColumnType.TEXT);
    }

    /** Whether this value can go in this part */
    public boolean fits(Object value) {
        if (value == ANY) return true;
        return type == ColumnType.INTEGER ? value instanceof Integer : value instanceof String;
    }

    @Override
    public String toString() {
        return name;
    }
}
