package com.colen.tempora.blocks.stress;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

// Debug block for stress testing the database write path. Placing it churns a 16x16x16 cube of random blocks above it
// every tick (4096 block changes/tick). Break it to stop.
public class StressTestBlock extends BlockContainer {

    public StressTestBlock() {
        super(Material.rock);
        setBlockName("temporaStressTestBlock");
        setBlockTextureName("command_block");
        setHardness(1.0F);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new StressTestTileEntity();
    }
}
