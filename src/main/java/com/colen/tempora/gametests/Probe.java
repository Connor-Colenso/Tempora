package com.colen.tempora.gametests;

import com.colen.tempora.loggers.generic.TemporaQueries;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;

// Runs a lookup through TemporaQueries over and over from a thenWaitUntil step until its result passes the test's
// assertions. Events get written in the background so a lookup straight after logging might not find them yet
//
// Each probe looks up as its own CapturingSender, Tempora runs one lookup at a time per sender
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

    // Starts query on the query thread unless one's still running, returns the result of the latest one that finished.
    // Fails until one has finished so the waiting step retries next tick
    public T poll(TemporaQueries.Query<T> query) {
        if (!running) {
            running = true;
            TemporaQueries.submit(sender, query, (target, result) -> {
                latest = result;
                finished = true;
                running = false;
            });
        }

        // A failed or timed out lookup tells the sender why instead of finishing
        helper.assertTrue(finished, "waiting for the lookup to finish. Sent so far:\n" + sender.text());
        return latest;
    }

    // Result of the latest finished lookup, for steps after the waiting one
    public T latest() {
        return latest;
    }
}
