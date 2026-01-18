package com.colen.tempora.utils;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class DebugUtils {

    private static final Random rand = new Random();

    /**
     * Randomizes blocks in a region from (x1, y1, z1) to (x2, y2, z2)
     * using a large subset of vanilla blocks and metadata.
     */
    public static void randomizeRegion(World world, int x1, int y1, int z1, int x2, int y2, int z2) {

        // Large selection of vanilla blocks + metadata
        BlockMeta[] options = new BlockMeta[] { new BlockMeta(Blocks.stone, 0), new BlockMeta(Blocks.cobblestone, 0),
            new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0),
            new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0),
            new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.air, 0),
            new BlockMeta(Blocks.air, 0), new BlockMeta(Blocks.mossy_cobblestone, 0),
            new BlockMeta(Blocks.brick_block, 0), new BlockMeta(Blocks.stonebrick, 0),
            new BlockMeta(Blocks.stonebrick, 1), new BlockMeta(Blocks.stonebrick, 2),
            new BlockMeta(Blocks.stonebrick, 3), new BlockMeta(Blocks.sandstone, 0), new BlockMeta(Blocks.sandstone, 1),
            new BlockMeta(Blocks.sandstone, 2), new BlockMeta(Blocks.dirt, 0), new BlockMeta(Blocks.grass, 0),
            new BlockMeta(Blocks.sand, 0), new BlockMeta(Blocks.gravel, 0), new BlockMeta(Blocks.clay, 0),
            new BlockMeta(Blocks.netherrack, 0), new BlockMeta(Blocks.quartz_block, 0),
            new BlockMeta(Blocks.quartz_block, 1), new BlockMeta(Blocks.quartz_block, 2), new BlockMeta(Blocks.ice, 0),
            new BlockMeta(Blocks.snow, 0), new BlockMeta(Blocks.obsidian, 0), new BlockMeta(Blocks.coal_block, 0),
            new BlockMeta(Blocks.iron_block, 0), new BlockMeta(Blocks.gold_block, 0),
            new BlockMeta(Blocks.diamond_block, 0), new BlockMeta(Blocks.lapis_block, 0),
            new BlockMeta(Blocks.redstone_block, 0), new BlockMeta(Blocks.brick_block, 0),
            new BlockMeta(Blocks.planks, 0), new BlockMeta(Blocks.planks, 1), new BlockMeta(Blocks.planks, 2),
            new BlockMeta(Blocks.planks, 3), new BlockMeta(Blocks.planks, 4), new BlockMeta(Blocks.planks, 5),
            new BlockMeta(Blocks.netherrack, 0), new BlockMeta(Blocks.glowstone, 0), new BlockMeta(Blocks.wool, 0),
            new BlockMeta(Blocks.wool, 1), new BlockMeta(Blocks.wool, 2), new BlockMeta(Blocks.wool, 3),
            new BlockMeta(Blocks.wool, 4), new BlockMeta(Blocks.wool, 5), new BlockMeta(Blocks.wool, 6),
            new BlockMeta(Blocks.wool, 7), new BlockMeta(Blocks.wool, 8), new BlockMeta(Blocks.wool, 9),
            new BlockMeta(Blocks.wool, 10), new BlockMeta(Blocks.wool, 11), new BlockMeta(Blocks.wool, 12),
            new BlockMeta(Blocks.wool, 13), new BlockMeta(Blocks.wool, 14), new BlockMeta(Blocks.wool, 15), };

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockMeta chosen = options[rand.nextInt(options.length)];
                    world.setBlock(x, y, z, chosen.block, chosen.meta, 3);
                }
            }
        }
    }

    // Simple inner class for block + metadata
    private static class BlockMeta {

        final Block block;
        final int meta;

        BlockMeta(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }
}
