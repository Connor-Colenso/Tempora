package com.colen.tempora.utils;

// How far back a lookup searches, from a t: filter. "3d" for the last 3 days or "3d-1d" (either way round) for between
// 3 and 1 days ago. Held as times ago not timestamps so the window gets worked out when the query runs
public final class TimeWindow {

    private static final TimeWindow ALL_TIME = new TimeWindow(-1, 0);

    private final long oldestSecondsAgo; // Negative means no limit
    private final long newestSecondsAgo; // 0 means now

    private TimeWindow(long oldestSecondsAgo, long newestSecondsAgo) {
        this.oldestSecondsAgo = oldestSecondsAgo;
        this.newestSecondsAgo = newestSecondsAgo;
    }

    public static TimeWindow allTime() {
        return ALL_TIME;
    }

    // Last so many seconds up to now
    public static TimeWindow last(long seconds) {
        return new TimeWindow(seconds, 0);
    }

    // E.g. "3d", or "3d-1d" for between 3 and 1 days ago. Throws a CommandException (shown to the sender) if invalid
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

    // Earliest timestamp in the window, ms
    public long oldestMillis(long nowMillis) {
        return oldestSecondsAgo < 0 ? 0 : nowMillis - oldestSecondsAgo * 1000L;
    }

    // Latest timestamp in the window, ms. Unbounded when it runs up to now so nothing logged a moment after the query
    // started gets left out
    public long newestMillis(long nowMillis) {
        return newestSecondsAgo == 0 ? Long.MAX_VALUE : nowMillis - newestSecondsAgo * 1000L;
    }
}
