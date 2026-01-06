package com.colen.tempora.utils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.command.CommandException;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.ChatComponentTimeAbsolute;
import com.colen.tempora.chat.ChatComponentTimeRelative;

public class TimeUtils {

    /**
     * Formats a given timestamp (in milliseconds) to a string saying how long ago it was, e.g. 1 hour ago.
     * Hovering over it will reveal string based on the default system timezone.
     * The format used is "yyyy-MM-dd HH:mm:ss z".
     *
     * @param epochMillis The unix epoch timestamp to format, in milliseconds.
     * @return A formatted date-time string.
     */
    public static IChatComponent formatTime(long epochMillis) {
        ChatComponentTimeRelative text = new ChatComponentTimeRelative(epochMillis);

        ChatComponentTimeAbsolute hoverText = new ChatComponentTimeAbsolute(epochMillis);
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Add hover
        text.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return text;
    }

    // E.g. 1hour, 2days, 1d etc
    public static long convertToSeconds(String timeDescription) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)")
            .matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException("Invalid time description. Expected e.g. '1week' or '5months' etc.");
        }

        long number = Long.parseLong(matcher.group(1));
        String raw = matcher.group(2)
            .toLowerCase(Locale.ROOT);

        // Map abbreviation to canonical.
        String unit = UNIT_ALIASES.get(raw);

        if (unit == null) {
            throw new CommandException("Invalid time description. Unsupported unit: " + raw);
        }

        return switch (unit) {
            case "second" -> number;
            case "minute" -> number * 60L;
            case "hour" -> number * 3_600L;
            case "day" -> number * 86_400L;
            case "week" -> number * 604_800L;
            case "month" -> number * 2_592_000L;
            case "year" -> number * 31_557_600L;
            case "decade" -> number * 315_576_000L;
            default -> throw new IllegalStateException("Unexpected value: " + unit);
        };
    }

    public static DurationParts relativeTimeAgoFormatter(long pastUnixEpochTimestampMillis) {

        double seconds = (Instant.now()
            .toEpochMilli() - pastUnixEpochTimestampMillis) / 1000.0;
        if (seconds < 60) {
            return new DurationParts(seconds, "time.ago.seconds");
        }

        double minutes = seconds / 60.0;
        if (minutes < 60) {
            return new DurationParts(minutes, "time.ago.minutes");
        }

        double hours = minutes / 60.0;
        if (hours < 24) {
            return new DurationParts(hours, "time.ago.hours");
        }

        double days = hours / 24.0;
        return new DurationParts(days, "time.ago.days");
    }

    // Utility class
    public static class DurationParts {

        public final double time;
        public final String translationKey;

        public DurationParts(double time, String translationKey) {
            this.time = time;
            this.translationKey = translationKey;
        }
    }

    // Conversion map for all our units.
    private static final Map<String, String> UNIT_ALIASES = new HashMap<>();
    static {
        add("second", "s", "sec", "secs", "second", "seconds");
        add("minute", "m", "min", "mins", "minute", "minutes");
        add("hour", "h", "hr", "hrs", "hour", "hours");
        add("day", "d", "day", "days");
        add("week", "w", "wk", "wks", "week", "weeks");
        add("month", "mo", "month", "months");
        add("year", "y", "yr", "yrs", "year", "years");
        add("decade", "de", "decade", "decades");
    }

    private static void add(String canonical, String... aliases) {
        for (String alias : aliases) {
            UNIT_ALIASES.put(alias, canonical);
        }
    }

}
