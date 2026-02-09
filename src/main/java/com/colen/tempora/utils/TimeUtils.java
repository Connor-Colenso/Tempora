package com.colen.tempora.utils;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

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
     * Hovering over it will reveal a string based on the default system timezone.
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

    // E.g. 1hour, 2days, 1d, etc...
    public static long convertToSeconds(String timeDescription) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)")
            .matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException("Invalid time description. Expected e.g. '1week' or '5months' etc.");
        }

        long number = Long.parseLong(matcher.group(1));
        String raw = matcher.group(2).toLowerCase();

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

    // Utility classes
    public static class DurationParts {

        public final double time;
        public final String translationKey;

        public DurationParts(double time, String translationKey) {
            this.time = time;
            this.translationKey = translationKey;
        }
    }

    private static class TimeUnit {

        public final double threshold; // max value before moving to the next unit
        public final double inSeconds; // how many seconds this unit represents
        public final String singularKey;
        public final String pluralKey;

        public TimeUnit(double threshold, double inSeconds, String singularKey, String pluralKey) {
            this.threshold = threshold;
            this.inSeconds = inSeconds;
            this.singularKey = singularKey;
            this.pluralKey = pluralKey;
        }
    }

    private static final TimeUnit[] TIME_UNITS = new TimeUnit[] {
        new TimeUnit(60, 1, "time.ago.second", "time.ago.seconds"),
        new TimeUnit(60, 60, "time.ago.minute", "time.ago.minutes"),
        new TimeUnit(24, 3600, "time.ago.hour", "time.ago.hours"),
        new TimeUnit(7, 86400, "time.ago.day", "time.ago.days"),
        new TimeUnit(4, 604800, "time.ago.week", "time.ago.weeks"),
        new TimeUnit(12, 604800 * 12, "time.ago.month", "time.ago.months"),
        new TimeUnit(10, 86400 * 365, "time.ago.year", "time.ago.years"),
        new TimeUnit(Double.MAX_VALUE, 86400 * 365 * 10, "time.ago.decade", "time.ago.decades") };

    public static DurationParts relativeTimeAgoFormatter(long pastUnixEpochMillis) {
        double elapsedSeconds = (Instant.now()
            .toEpochMilli() - pastUnixEpochMillis) / 1000.0;

        for (TimeUnit unit : TIME_UNITS) {
            double value = elapsedSeconds / unit.inSeconds;
            if (value < unit.threshold) {
                // A bit of a hack, but we want to know how this number will render to the user.
                String key = (formatNumber(value).equals("1")) ? unit.singularKey : unit.pluralKey;
                return new DurationParts(value, key);
            }
            elapsedSeconds = value; // scale down to the next unit
        }

        // fallback (should never reach)
        return new DurationParts(elapsedSeconds, "time.ago.years");
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
