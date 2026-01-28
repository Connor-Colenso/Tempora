package com.colen.tempora.commands.command_base;

public final class CommandArg {

    public final String argNames;
    public final String descriptionLangKey;

    public CommandArg(String argNames, String descriptionLangKey) {
        this.argNames = argNames;
        this.descriptionLangKey = descriptionLangKey;
    }

}
