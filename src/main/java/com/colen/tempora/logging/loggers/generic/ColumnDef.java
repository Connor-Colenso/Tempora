package com.colen.tempora.logging.loggers.generic;

public class ColumnDef {

    public final String name;
    public final String type;
    public final String extraCondition;

    public ColumnDef(String name, String type, String extraCondition) {
        this.name = name;
        this.type = type;
        this.extraCondition = extraCondition;

        if (this.extraCondition.toUpperCase().contains("DEFAULT")) {
            throw new IllegalArgumentException("A column def must have a default condition.");
        }

    }
}
