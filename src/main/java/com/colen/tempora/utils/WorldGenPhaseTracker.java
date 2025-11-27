package com.colen.tempora.utils;

public class WorldGenPhaseTracker {
    // TODO determine if needed?
    // public static final ThreadLocal<Boolean> IN_WORLD_GEN = ThreadLocal.withInitial(() -> false);

    public static boolean IN_WORLD_GEN = false;

    public static boolean isInWorldGen() {
        return IN_WORLD_GEN;
    }
}
