package com.colen.tempora.loggers.inventory;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

// Item amounts keyed by ItemKey, for comparing what an inventory held before and after
public final class ItemCounts {

    private final Map<ItemKey, Long> counts = new LinkedHashMap<>();

    public void add(@Nullable ItemKey key, long amount) {
        if (key == null || amount == 0) return;
        counts.merge(key, amount, Long::sum);
    }

    public long get(ItemKey key) {
        return counts.getOrDefault(key, 0L);
    }

    public Map<ItemKey, Long> asMap() {
        return counts;
    }

    // Per item, how much more after has than before. Unchanged items are left out
    public static Map<ItemKey, Long> difference(ItemCounts before, ItemCounts after) {
        Map<ItemKey, Long> result = new LinkedHashMap<>();

        for (Map.Entry<ItemKey, Long> entry : after.counts.entrySet()) {
            long change = entry.getValue() - before.get(entry.getKey());
            if (change != 0) result.put(entry.getKey(), change);
        }

        for (Map.Entry<ItemKey, Long> entry : before.counts.entrySet()) {
            if (!after.counts.containsKey(entry.getKey()) && entry.getValue() != 0) {
                result.put(entry.getKey(), -entry.getValue());
            }
        }

        return result;
    }
}
