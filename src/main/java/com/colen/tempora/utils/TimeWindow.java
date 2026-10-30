package com.colen.tempora.utils;

/**
 * How far back a lookup searches, as a t: filter gives it: "3d" for the last 3 days, or "3d-1d" (either way round) for
 * between 3 and 1 days ago. Held as times ago rather than timestamps, so the window is worked out when the query runs.
 */
public final class TimeWindow {

    private static final TimeWindow ALL_TIME = new TimeWindow(-1, 0);

    private final long oldestSecondsAgo; // Negative means no limit.
    private final long newestSecondsAgo; // 0 means now.

    private TimeWindow(long oldestSecondsAgo, long newestSecondsAgo) {
        this.oldestSecondsAgo = oldestSecondsAgo;
        this.newestSecondsAgo = newestSecondsAgo;
    }

    public static TimeWindow allTime() {
        return ALL_TIME;
    }

    // The last so many seconds, up to now.
    public static TimeWindow last(long seconds) {
        return new TimeWindow(seconds, 0);
    }

    // E.g. "3d", or "3d-1d" for between 3 and 1 days ago. Throws a CommandException, shown to the sender, if invalid.
    public static TimeWindow parse(String filter) {
        int dash = filter.indexOf('-');
        if (dash < 0) return new TimeWindow(TimeUtils.convertToSeconds(filter), 0);

        String first = filter.substring(0, dash);
        String second = filter.substring(dash + 1);
        long firstSeconds = TimeUtils.convertToSeconds(first);
        long secondSeconds = TimeUtils.convertToSeconds(second);
        return firstSeconds >= secondSeconds ? new TimeWindow(firstSeconds, secondSeconds)
            : new TimeWindow(secondSeconds, firstSeconds);
    }

    // The earliest timestamp in the window, in milliseconds.
    public long oldestMillis(long nowMillis) {
        return oldestSecondsAgo < 0 ? 0 : nowMillis - oldestSecondsAgo * 1000L;
    }

    // The latest timestamp in the window, in milliseconds. Unbounded when it runs up to now, so nothing logged a moment
    // after the query started is left out.
    public long newestMillis(long nowMillis) {
        return newestSecondsAgo == 0 ? Long.MAX_VALUE : nowMillis - newestSecondsAgo * 1000L;
    }
}
