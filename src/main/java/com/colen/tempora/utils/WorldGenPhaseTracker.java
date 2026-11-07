package com.colen.tempora.utils;

// Whether this thread is generating the world, so its block changes don't get logged. Set around chunk population and
// terrain gen in MixinChunkProviderServer and around the bonus chest in MixinWorldServer. Block updates world gen
// leaves behind only partly count, see WorldGenTicks
public final class WorldGenPhaseTracker {

    // Nesting depth per thread
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
