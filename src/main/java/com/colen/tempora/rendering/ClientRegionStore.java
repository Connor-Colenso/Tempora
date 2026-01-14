package com.colen.tempora.rendering;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;

public final class ClientRegionStore {

    public static final long RENDER_DURATION_MILLISECONDS = 10_000;

    private static final Map<String, TemporaWorldRegion> REGIONS = new ConcurrentHashMap<>();

    private ClientRegionStore() {}

    public static void add(TemporaWorldRegion region) {
        REGIONS.put(region.getRegionUUID(), region);
    }

    public static Collection<TemporaWorldRegion> all() {
        return REGIONS.values();
    }

    public static void expire() {
        for (TemporaWorldRegion region : REGIONS.values()) {
            if (System.currentTimeMillis() - region.getRenderStartTimeMs() > RENDER_DURATION_MILLISECONDS) {
                remove(region.getRegionUUID());
            }
        }
    }

    public static void remove(String uuid) {
        REGIONS.remove(uuid);
    }
}
