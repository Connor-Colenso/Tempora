package com.colen.tempora.commands.command_base;

import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TimeUtils;
import net.minecraft.util.IChatComponent;

import java.util.Objects;
import java.util.function.Supplier;

public final class ArgParser {

    private final String[] args;

    public ArgParser(String[] args) {
        this.args = Objects.requireNonNull(args);
    }

    /* ------------------------------------------------------------
     * Core helper
     * ------------------------------------------------------------ */

    private <T> ParseResult<T> parse(
        int index,
        IChatComponent error,
        Supplier<T> parser
    ) {

        if (index < 0 || index >= args.length) {
            return ParseResult.error(error);
        }

        try {
            T value = parser.get();
            if (value == null) {
                return ParseResult.error(error);
            }
            return ParseResult.ok(value);
        } catch (Exception e) {
            return ParseResult.error(error);
        }
    }

    /* ------------------------------------------------------------
     * Argument types
     * ------------------------------------------------------------ */

    public ParseResult<String> string(int index, IChatComponent error) {
        return parse(index, error, () -> {
            String v = args[index];
            if (v.isEmpty()) throw new IllegalArgumentException();
            return v;
        });
    }

    public ParseResult<Integer> positiveInt(int index, IChatComponent error) {
        return parse(index, error, () -> {
            int v = Integer.parseInt(args[index]);
            if (v < 0) throw new IllegalArgumentException();
            return v;
        });
    }

    public ParseResult<Integer> integer(int index, IChatComponent error) {
        return parse(index, error, () ->
            Integer.parseInt(args[index])
        );
    }

    public ParseResult<Double> dbl(int index, IChatComponent error) {
        return parse(index, error, () ->
            Double.parseDouble(args[index])
        );
    }

    public ParseResult<Long> timeSeconds(int index, IChatComponent error) {
        return parse(index, error, () ->
            TimeUtils.convertToSeconds(args[index])
        );
    }

    public ParseResult<String> loggerName(int index, IChatComponent error) {
        return parse(index, error, () -> {
            String v = args[index];
            if (CommandUtils.validateLoggerName(v) == null) {
                throw new IllegalArgumentException();
            }
            return v;
        });
    }

    /* ------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------ */

    public boolean hasMinArgs(int required) {
        return args.length >= required;
    }
}
