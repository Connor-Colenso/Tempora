package com.colen.tempora.logging.loggers.generic;

public class ColumnDef {

    public final String name;
    public final String type;
    public final String extraCondition;

    public ColumnDef(String name, String type, String extraCondition) {
        this.name = name;
        this.type = type;
        this.extraCondition = extraCondition;
    }

    public ColumnDef(String name, String type) {
        this.name = name;
        this.type = type;
        this.extraCondition = null;
    }
}
