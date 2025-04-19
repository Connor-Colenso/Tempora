package com.colen.tempora.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public class BlockUtils {

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>();

    /**
     * Get the localized name of a block from its ID and metadata, using caching to optimize.
     */
    public static String getLocalizedName(int blockId, int metadata) {
        if (blockId == 0 && metadata == 0) {
            return "Air";
        }

        String cacheKey = blockId + ":" + metadata;
        if (nameCache.containsKey(cacheKey)) {
            return nameCache.get(cacheKey);
        }

        Block block = Block.getBlockById(blockId);
        if (block == null) {
            return "[Unknown Block]";
        }

        ItemStack itemStack = new ItemStack(block, 1, metadata);
        if (itemStack.getItem() == null) {
            return "[Unknown Block]";
        }

        String localizedName = itemStack.getDisplayName();
        nameCache.put(cacheKey, localizedName);
        return localizedName;
    }

}
