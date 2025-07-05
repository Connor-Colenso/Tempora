package com.colen.tempora.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;

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

    public static IChatComponent getRelativeTimeFromUnix(long pastTimestamp, String timezoneId) {
        // Convert input to Instants
        Instant now = Instant.now();
        Instant past = Instant.ofEpochMilli(pastTimestamp);
        Duration duration = Duration.between(past, now);

        // Calculate time units
        double seconds = duration.toMillis() / 1000.0;
        double minutes = seconds / 60.0;
        double hours = minutes / 60.0;
        double days = hours / 24.0;
        double years = days / 365.0;
        double decades = years / 10.0;

        // Format the exact time in the given timezone
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezoneId);
        } catch (DateTimeException e) {
            zoneId = ZoneOffset.UTC; // fallback
        }

        ZonedDateTime localDateTime = ZonedDateTime.ofInstant(past, zoneId);
        String formattedTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        // Choose appropriate message key and formatted number
        String key;
        String formattedValue;

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

        // Create translated component
        ChatComponentTranslation translated = new ChatComponentTranslation(key, formattedValue);

        // Add hover text showing the exact timestamp
        translated.setChatStyle(
            new ChatStyle().setChatHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("§7" + formattedTime))));

        return translated;
    }

    public static long convertToSeconds(String timeDescription) {
        // Use regular expressions to separate numbers from text
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([a-zA-Z]+)")
            .matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException(
                "Invalid time description. It should be in the format e.g., '1week' or '5months'.");
        }

        long number = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2)
            .toLowerCase();
        // Remove trailing 's' if present to handle both singular and plural forms
        if (unit.endsWith("s")) {
            unit = unit.substring(0, unit.length() - 1);
        }

        return switch (unit) {
            case "second" -> number;
            case "minute" -> number * 60;
            case "hour" -> number * 3600;
            case "day" -> number * 86400;
            case "week" -> number * 604800;
            case "month" -> number * 2592000; // Approximation using 30 days per month
            case "year" -> number * 31557600; // Using 365.25 days per year
            case "decade" -> number * 315576000; // 10 years
            default -> throw new CommandException(
                "Unsupported time unit. Use one of: second, minute, hour, day, week, month, year, decade.");
        };
    }
}
