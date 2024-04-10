package com.myname.mymodid.Utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeUtils {

    /**
     * Formats a given timestamp (in milliseconds) to a string based on the default system timezone.
     * The format used is "yyyy-MM-dd HH:mm:ss".
     *
     * @param epochMillis The timestamp to format, in milliseconds.
     * @return A formatted date-time string.
     */
    public static String formatTime(long epochMillis) {
        // Convert epoch time to an Instant
        Instant instant = Instant.ofEpochMilli(epochMillis);

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
}
