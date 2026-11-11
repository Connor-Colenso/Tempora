package com.colen.tempora.loggers.generic.column.names;

/** Where a {@link NameRole} puts the names of its things, see {@link NameRole#shownNames} */
@FunctionalInterface
public interface ShownNames {

    /**
     * One thing, with an Integer for each {@link NamePart#integer} part and a String for each {@link NamePart#text}
     * part, or {@link NamePart#ANY}. Ones that don't fit the parts get skipped
     */
    void add(String shownName, Object... values);
}
