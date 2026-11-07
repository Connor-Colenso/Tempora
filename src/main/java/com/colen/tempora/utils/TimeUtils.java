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
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class TimeUtils {

    // How long ago a timestamp (ms) was, e.g. 1.5h ago. Hover shows the date in the system timezone as "yyyy-MM-dd
    // HH:mm:ss z"
    public static IChatComponent formatTime(long epochMillis) {
        ChatComponentTimeRelative text = new ChatComponentTimeRelative(epochMillis);

        ChatComponentTimeAbsolute hoverText = new ChatComponentTimeAbsolute(epochMillis);
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        text.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return text;
    }

    // E.g. 1hour, 2days, 1d etc
    public static long convertToSeconds(String timeDescription) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)")
            .matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException("tempora.time.invalid", timeDescription);
        }

        String raw = matcher.group(2)
            .toLowerCase(Locale.ROOT);

        // Map the abbreviation to the canonical unit
        String unit = UNIT_ALIASES.get(raw);

        if (unit == null) {
            throw new CommandException("tempora.time.unsupported_unit", raw);
        }

        long unitSeconds = switch (unit) {
            case "second" -> 1L;
            case "minute" -> 60L;
            case "hour" -> 3_600L;
            case "day" -> 86_400L;
            case "week" -> 604_800L;
            case "month" -> 2_592_000L;
            case "year" -> 31_557_600L;
            case "decade" -> 315_576_000L;
            default -> throw new IllegalStateException("Unexpected value: " + unit);
        };

        // Anything longer is a typo and would overflow once turned into ms
        try {
            long seconds = Math.multiplyExact(Long.parseLong(matcher.group(1)), unitSeconds);
            if (seconds > MAX_SECONDS) throw new ArithmeticException();
            return seconds;
        } catch (NumberFormatException | ArithmeticException tooLong) {
            throw new CommandException("tempora.time.too_long", new ChatComponentNumber(MAX_YEARS));
        }
    }

    private static final int MAX_YEARS = 1000;
    private static final long MAX_SECONDS = MAX_YEARS * 31_557_600L;

    public static class DurationParts {

        public final double time;
        public final String translationKey;

        public DurationParts(double time, String translationKey) {
            this.time = time;
            this.translationKey = translationKey;
        }
    }

    private static class TimeUnit {

        public final double inSeconds; // Seconds in this unit
        public final String key;

        public TimeUnit(double inSeconds, String key) {
            this.inSeconds = inSeconds;
            this.key = key;
        }
    }

    // Smallest first. Same unit lengths as convertToSeconds so "1mo" typed into a command reads back as 1mo ago
    private static final TimeUnit[] TIME_UNITS = new TimeUnit[] { new TimeUnit(1, "tempora.time.ago.seconds"),
        new TimeUnit(60, "tempora.time.ago.minutes"), new TimeUnit(3_600, "tempora.time.ago.hours"),
        new TimeUnit(86_400, "tempora.time.ago.days"), new TimeUnit(604_800, "tempora.time.ago.weeks"),
        new TimeUnit(2_592_000, "tempora.time.ago.months"), new TimeUnit(31_557_600, "tempora.time.ago.years") };

    // How long ago a time was in the biggest unit it's reached at least one of, e.g. 6.4 and "6.4m ago"
    public static DurationParts relativeTimeAgoFormatter(long pastUnixEpochMillis) {
        double elapsedSeconds = (Instant.now()
            .toEpochMilli() - pastUnixEpochMillis) / 1000.0;

        if (elapsedSeconds < 1) return new DurationParts(0, "tempora.time.ago.now");

        TimeUnit unit = TIME_UNITS[0];
        for (TimeUnit candidate : TIME_UNITS) {
            if (elapsedSeconds >= candidate.inSeconds) {
                unit = candidate;
            }
        }

        return new DurationParts(elapsedSeconds / unit.inSeconds, unit.key);
    }

    // Every unit alias
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
