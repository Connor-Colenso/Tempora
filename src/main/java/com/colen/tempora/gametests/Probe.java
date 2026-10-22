package com.colen.tempora.gametests;

import com.colen.tempora.loggers.generic.TemporaQueries;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;

/**
 * Runs a lookup through {@link TemporaQueries} again and again from a {@code thenWaitUntil} step, until its result
 * passes the test's assertions. Events are written to the database in the background, so a lookup straight after
 * logging may not find them yet.
 * <p>
 * Each probe looks up as its own {@link CapturingSender}, as Tempora runs one lookup at a time per sender.
 */
public final class Probe<T> {

    private final GameTestHelper helper;
    private final CapturingSender sender;
    private boolean running;
    private boolean finished;
    private T latest;

    public Probe(GameTestHelper helper, String name) {
        this.helper = helper;
        this.sender = new CapturingSender(name, helper.getWorld());
    }

    /**
     * Starts {@code query} on the query thread unless one is still running, and returns the result of the latest one
     * that finished. Fails, so the waiting step tries again next tick, until one has finished.
     */
    public T poll(TemporaQueries.Query<T> query) {
        if (!running) {
            running = true;
            TemporaQueries.submit(sender, query, (target, result) -> {
                latest = result;
                finished = true;
                running = false;
            });
        }

        // A failed or timed out lookup tells the sender why, rather than finishing.
        helper.assertTrue(finished, "waiting for the lookup to finish. Sent so far:\n" + sender.text());
        return latest;
    }

    // The result of the latest lookup that finished, for steps after the waiting one.
    public T latest() {
        return latest;
    }
}
