package com.colen.tempora.utils;

import net.minecraft.command.CommandException;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.colen.tempora.config.Config.formatCategory;

public class TimeUtils {

    private static boolean ENABLE_RELATIVE_TIME;

    public static void handleConfig(Configuration config) {
        ENABLE_RELATIVE_TIME = config.getBoolean(
            "enableRelativeTime",
            formatCategory,
            true,
            "Gets the relative time instead of a timestamp e.g. 6 hours ago.");
    }

    /**
     * Formats a given timestamp (in milliseconds) to a string based on the default system timezone.
     * The format used is "yyyy-MM-dd HH:mm:ss".
     *
     * @param epochMillis The timestamp to format, in milliseconds.
     * @return A formatted date-time string.
     */
    public static String formatTime(long epochMillis) {
        if (ENABLE_RELATIVE_TIME) {
            return getRelativeTimeFromUnix(epochMillis);
        } else {
            return getExactTimeStampFromUnix(epochMillis);
        }
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

    public static String getRelativeTimeFromUnix(long pastTimestamp) {
        // Current timestamp and past timestamp as Instant objects
        Instant now = Instant.now();
        Instant past = Instant.ofEpochMilli(pastTimestamp);

        // Calculate the duration between now and the past timestamp
        Duration duration = Duration.between(past, now);

        // Get total milliseconds and convert to seconds for more precise calculations
        double milliseconds = duration.toMillis();  // Milliseconds since the past timestamp
        double seconds = milliseconds / 1000.0;
        double minutes = seconds / 60.0;
        double hours = minutes / 60.0;
        double days = hours / 24.0;
        double years = days / 365.0;
        double decades = years / 10.0;

        // Determine the largest time unit to display and format it to 1 decimal place
        if (decades >= 1) {
            return String.format(StatCollector.translateToLocal("time.ago.decades"), String.format("%.1f", decades));
        } else if (years >= 1) {
            return String.format(StatCollector.translateToLocal("time.ago.years"), String.format("%.1f", years));
        } else if (days >= 1) {
            return String.format(StatCollector.translateToLocal("time.ago.days"), String.format("%.1f", days));
        } else if (hours >= 1) {
            return String.format(StatCollector.translateToLocal("time.ago.hours"), String.format("%.1f", hours));
        } else if (minutes >= 1) {
            return String.format(StatCollector.translateToLocal("time.ago.minutes"), String.format("%.1f", minutes));
        } else {
            return String.format(StatCollector.translateToLocal("time.ago.seconds"), String.format("%.1f", seconds));
        }
    }

    public static long convertToSeconds(String timeDescription) {
        // Use regular expressions to separate numbers from text
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([a-zA-Z]+)").matcher(timeDescription);
        if (!matcher.matches()) {
            throw new CommandException("Invalid time description. It should be in the format 'numberunit', e.g., '1week' or '5months'.");
        }

        long number = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();
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
            default -> throw new CommandException("Unsupported time unit. Use one of: second, minute, hour, day, week, month, year, decade.");
        };
    }
}
