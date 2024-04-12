package com.myname.mymodid.Utils;

import net.minecraftforge.common.config.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.myname.mymodid.Config.Config.formatCategory;

public class TimeUtils {

    private static boolean RELATIVE_TIME;

    public static void handleConfig(Configuration config) {
        RELATIVE_TIME = config.getBoolean(
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
        if (RELATIVE_TIME) {
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

        // Use BigDecimal for precise calculations and rounding
        BigDecimal days = new BigDecimal(duration.toDays());
        BigDecimal hours = new BigDecimal(duration.toHours());
        BigDecimal minutes = new BigDecimal(duration.toMinutes());

        // Conversions for years and decades
        BigDecimal years = days.divide(new BigDecimal(365), 2, RoundingMode.HALF_UP);
        BigDecimal decades = years.divide(new BigDecimal(10), 1, RoundingMode.HALF_UP);

        // Determine the largest time unit to display and format it
        if (decades.compareTo(BigDecimal.ONE) >= 0) {
            return decades + " decades ago";
        } else if (years.compareTo(BigDecimal.ONE) >= 0) {
            return years.setScale(1, RoundingMode.HALF_UP) + " years ago";
        } else if (days.compareTo(BigDecimal.ONE) >= 0) {
            BigDecimal exactDays = hours.divide(new BigDecimal(24), 1, RoundingMode.HALF_UP);
            return exactDays + " days ago";
        } else if (hours.compareTo(BigDecimal.ONE) >= 0) {
            BigDecimal exactHours = minutes.divide(new BigDecimal(60), 1, RoundingMode.HALF_UP);
            return exactHours + " hours ago";
        } else {
            BigDecimal exactMinutes = new BigDecimal(duration.toMinutes());
            return exactMinutes.setScale(1, RoundingMode.HALF_UP) + " minutes ago";
        }
    }
}
