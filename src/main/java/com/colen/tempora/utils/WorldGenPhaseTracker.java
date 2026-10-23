package com.colen.tempora.utils;

public final class WorldGenPhaseTracker {

    // Per-thread nesting depth
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private WorldGenPhaseTracker() {}

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get();
        DEPTH.set(depth <= 1 ? 0 : depth - 1);
    }

    public static boolean isWorldGen() {
        return DEPTH.get() > 0;
    }
}
