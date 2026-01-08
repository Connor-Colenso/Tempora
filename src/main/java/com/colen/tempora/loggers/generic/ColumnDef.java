package com.colen.tempora.loggers.generic;

public class ColumnDef {

    public final String name;
    public final String type;
    public final String extraCondition;

    public ColumnDef(String name, String type, String extraCondition) {
        this.name = name;
        this.type = type;
        this.extraCondition = extraCondition;

        // String upper = extraCondition.toUpperCase()
        // .trim();
        // boolean isPrimary = upper.startsWith("PRIMARY KEY");
        // boolean hasDefault = upper.contains("DEFAULT");
        //
        // if (!hasDefault && !isPrimary) {
        // throw new IllegalArgumentException("A column def must have a default condition (except primary key).");
        // }
    }

    public String getName() {
        return name;
    }
}
