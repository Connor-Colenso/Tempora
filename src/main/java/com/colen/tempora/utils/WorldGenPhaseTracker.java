package com.colen.tempora.utils;

public final class WorldGenPhaseTracker {

    public enum Phase {
        NONE,
        BASE_TERRAIN, // ChunkProviderGenerate.populate
        MOD_FEATURES // GameRegistry.generateWorld (mod hooks)
    }

    // Per-thread nesting depth
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private static final ThreadLocal<Phase> CURRENT_PHASE = ThreadLocal.withInitial(() -> Phase.NONE);

    private WorldGenPhaseTracker() {}

    public static void enter(Phase phase) {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);

        // Only set phase on the outermost entry
        if (depth == 0) {
            CURRENT_PHASE.set(phase);
        }
    }

    public static void exit() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.set(0);
            CURRENT_PHASE.set(Phase.NONE);
        } else {
            DEPTH.set(depth - 1);
        }
    }

    public static boolean isWorldGen() {
        return DEPTH.get() > 0;
    }

    public static Phase currentPhase() {
        return CURRENT_PHASE.get();
    }
}
