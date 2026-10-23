package com.colen.tempora.blocks.stress;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

public class StressTestTileEntity extends TileEntity {

    public static final int SIZE = 16;

    // Solid, non-gravity, non-tile-entity blocks with no update behaviour of their own, so every change is a plain
    // block swap. Each entry is {block, maxMeta}; a random meta in [0, maxMeta] is picked.
    private static final Object[][] PALETTE = { { Blocks.stone, 0 }, { Blocks.cobblestone, 0 }, { Blocks.dirt, 0 },
        { Blocks.planks, 5 }, { Blocks.log, 3 }, { Blocks.wool, 15 }, { Blocks.stained_hardened_clay, 15 },
        { Blocks.hardened_clay, 0 }, { Blocks.glass, 0 }, { Blocks.stained_glass, 15 }, { Blocks.brick_block, 0 },
        { Blocks.stonebrick, 3 }, { Blocks.sandstone, 2 }, { Blocks.obsidian, 0 }, { Blocks.netherrack, 0 },
        { Blocks.nether_brick, 0 }, { Blocks.quartz_block, 2 }, { Blocks.end_stone, 0 },
        { Blocks.mossy_cobblestone, 0 }, { Blocks.iron_block, 0 }, { Blocks.gold_block, 0 },
        { Blocks.diamond_block, 0 }, { Blocks.emerald_block, 0 }, { Blocks.lapis_block, 0 }, { Blocks.coal_block, 0 },
        { Blocks.bookshelf, 0 }, { Blocks.hay_block, 0 }, { Blocks.clay, 0 }, { Blocks.packed_ice, 0 },
        { Blocks.snow, 0 }, };

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;

        Random rand = worldObj.rand;
        int maxY = Math.min(yCoord + SIZE + 5, worldObj.getHeight() - 1);

        for (int y = yCoord + 1; y <= maxY; y++) {
            for (int dx = 0; dx < SIZE; dx++) {
                for (int dz = 0; dz < SIZE; dz++) {
                    Object[] entry = PALETTE[rand.nextInt(PALETTE.length)];
                    Block block = (Block) entry[0];
                    int meta = rand.nextInt((Integer) entry[1] + 1);
                    // Flag 2: sync to clients, skip neighbour notifications.
                    worldObj.setBlock(xCoord - SIZE / 2 + dx, y, zCoord - SIZE / 2 + dz, block, meta, 2);
                }
            }
        }
    }
}
