package com.colen.tempora.rendering;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;

public final class ClientRegionStore {

    public static final long RENDER_DURATION_MILLISECONDS = 10_000;

    private static final Map<String, RegionToRender> REGIONS = new ConcurrentHashMap<>();

    private ClientRegionStore() {}

    public static void add(RegionToRender region) {
        REGIONS.putIfAbsent(region.getRegionUUID(), region);
    }

    public static Collection<RegionToRender> all() {
        return REGIONS.values();
    }

    public static void expire() {
        for (RegionToRender region : REGIONS.values()) {
            if (System.currentTimeMillis() - region.getRenderStartTimeMs() > RENDER_DURATION_MILLISECONDS) {
                remove(region.getRegionUUID());
            }
        }
    }

    public static void remove(String uuid) {
        REGIONS.remove(uuid);
    }
}
