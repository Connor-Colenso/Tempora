package com.colen.tempora.commands.command_base;

import net.minecraft.util.IChatComponent;

public final class ParseResult<T> {

    private final T value;
    private final IChatComponent error;

    private ParseResult(T value, IChatComponent error) {
        this.value = value;
        this.error = error;
    }

    public static <T> ParseResult<T> ok(T value) {
        return new ParseResult<>(value, null);
    }

    public static <T> ParseResult<T> error(IChatComponent error) {
        return new ParseResult<>(null, error);
    }

    public boolean isOk() {
        return error == null;
    }

    public T value() {
        return value;
    }

    public IChatComponent error() {
        return error;
    }
}
