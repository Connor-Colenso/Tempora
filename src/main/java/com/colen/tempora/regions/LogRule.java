package com.colen.tempora.regions;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

// A region's rule for one logger. A region with no rule for a logger has no say over it
public enum LogRule {

    ON,
    OFF;

    public static @Nullable LogRule fromString(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
