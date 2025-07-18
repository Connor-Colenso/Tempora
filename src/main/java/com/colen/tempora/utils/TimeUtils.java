package com.colen.tempora.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.command.CommandException;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.*;
import net.minecraftforge.common.config.Configuration;

public class TimeUtils {

    public static @Nullable String getTimeZone(String uuid) {
        return UUIDtoTimeZone.getOrDefault(uuid, null);
    }

    public static void setTimeZone(String uuid, String timezone) {
        UUIDtoTimeZone.put(uuid, timezone);
    }

    public static HashMap<String, String> UUIDtoTimeZone = new HashMap<>();

    public static void handleConfig(Configuration config) {

    }

    /**
     * Formats a given timestamp (in milliseconds) to a string based on the default system timezone.
     * The format used is "yyyy-MM-dd HH:mm:ss".
     *
     * @param epochMillis The timestamp to format, in milliseconds.
     * @return A formatted date-time string.
     */
    public static IChatComponent formatTime(long epochMillis, String uuid) {
        return getRelativeTimeFromUnix(epochMillis, getTimeZone(uuid));
    }

    public static String getExactTimeStampFromUnix(long pastTimestamp) {
        // Convert epoch time to an Instant
        Instant instant = Instant.ofEpochMilli(pastTimestamp);

        // Get the system default time zone
        ZoneId zoneId = ZoneId.systemDefault();

        // Convert Instant to ZonedDateTime in the default timezone
        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        // Create a formatter (this can be customized as needed)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withLocale(Locale.getDefault())
            .withZone(zoneId);

        // Format the ZonedDateTime to a string
        return zonedDateTime.format(formatter);
    }

    public static String getRelativeTimeString(long pastTimestamp) {
        Instant now = Instant.now();
        Instant past = Instant.ofEpochMilli(pastTimestamp);
        Duration duration = Duration.between(past, now);

        double seconds = duration.toMillis() / 1000.0;
        double minutes = seconds / 60.0;
        double hours = minutes / 60.0;
        double days = hours / 24.0;
        double years = days / 365.0;
        double decades = years / 10.0;

        String formattedValue;
        String key;

        if (decades >= 1) {
            key = "time.ago.decades";
            formattedValue = String.format("%.1f", decades);
        } else if (years >= 1) {
            key = "time.ago.years";
            formattedValue = String.format("%.1f", years);
        } else if (days >= 1) {
            key = "time.ago.days";
            formattedValue = String.format("%.1f", days);
        } else if (hours >= 1) {
            key = "time.ago.hours";
            formattedValue = String.format("%.1f", hours);
        } else if (minutes >= 1) {
            key = "time.ago.minutes";
            formattedValue = String.format("%.1f", minutes);
        } else {
            key = "time.ago.seconds";
            formattedValue = String.format("%.1f", seconds);
        }
        // Example output: "3.2 days"
        return formattedValue + " " + key.replace("time.ago.", "");
    }

    public static IChatComponent getRelativeTimeFromUnix(long pastTimestamp, String timezoneId) {
        String agoString = getRelativeTimeString(pastTimestamp);

        // This code remains unchanged (except fallback if you want):
        Instant past = Instant.ofEpochMilli(pastTimestamp);
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezoneId);
        } catch (DateTimeException e) {
            zoneId = ZoneOffset.UTC;
        }

        ZonedDateTime localDateTime = ZonedDateTime.ofInstant(past, zoneId);
        String formattedTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        // Main display (replace with translation key if you want):
        ChatComponentText text = new ChatComponentText(agoString);

        // Add hover
        text.setChatStyle(
            new ChatStyle().setChatHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("§7" + formattedTime)))
        );

        return text;
    }

    // --------------------------------------------------------------------
    // Alias → canonical (all lower‑case, no trailing whitespace)
    // --------------------------------------------------------------------
    private static final Map<String, String> UNIT_ALIASES = new HashMap<>();
    static {
        // seconds
        UNIT_ALIASES.put("s", "second");
        UNIT_ALIASES.put("sec", "second");
        UNIT_ALIASES.put("secs", "second");
        UNIT_ALIASES.put("second", "second");
        UNIT_ALIASES.put("seconds", "second");

        // minutes
        UNIT_ALIASES.put("m", "minute");
        UNIT_ALIASES.put("min", "minute");
        UNIT_ALIASES.put("mins", "minute");
        UNIT_ALIASES.put("minute", "minute");
        UNIT_ALIASES.put("minutes", "minute");

        // hours
        UNIT_ALIASES.put("h", "hour");
        UNIT_ALIASES.put("hr", "hour");
        UNIT_ALIASES.put("hrs", "hour");
        UNIT_ALIASES.put("hour", "hour");
        UNIT_ALIASES.put("hours", "hour");

        // days
        UNIT_ALIASES.put("d", "day");
        UNIT_ALIASES.put("day", "day");
        UNIT_ALIASES.put("days", "day");

        // weeks
        UNIT_ALIASES.put("w", "week");
        UNIT_ALIASES.put("wk", "week");
        UNIT_ALIASES.put("wks", "week");
        UNIT_ALIASES.put("week", "week");
        UNIT_ALIASES.put("weeks", "week");

        // months
        UNIT_ALIASES.put("mo", "month");
        UNIT_ALIASES.put("month", "month");
        UNIT_ALIASES.put("months", "month");

        // years
        UNIT_ALIASES.put("y", "year");
        UNIT_ALIASES.put("yr", "year");
        UNIT_ALIASES.put("yrs", "year");
        UNIT_ALIASES.put("year", "year");
        UNIT_ALIASES.put("years", "year");

        // decades
        UNIT_ALIASES.put("decade", "decade");
        UNIT_ALIASES.put("decades", "decade");
    }

    public static long convertToSeconds(String timeDescription) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*([a-zA-Z]+)")
            .matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException("Invalid time description. Expected e.g. '1week' or '5months' etc.");
        }

        long number = Long.parseLong(matcher.group(1));
        String raw = matcher.group(2)
            .toLowerCase(Locale.ROOT);

        // Map abbreviation/plural to canonical singular (or fall back to raw)
        String unit = UNIT_ALIASES.getOrDefault(raw, raw);

        return switch (unit) {
            case "second" -> number;
            case "minute" -> number * 60L;
            case "hour" -> number * 3_600L;
            case "day" -> number * 86_400L;
            case "week" -> number * 604_800L;
            case "month" -> number * 2_592_000L;
            case "year" -> number * 31_557_600L;
            case "decade" -> number * 315_576_000L;
            default -> throw new CommandException("Unsupported time unit. Allowed: s, m, h, d, w, mo, y, decade.");
        };
    }

}
