package com.colen.tempora.rendering.FakeWorld;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

public class FakeWorld implements IBlockAccess {

    public Block block = null;
    public TileEntity tileEntity = new TileEntity();
    public int metadata;

    // RenderUtils draws the block at the origin
    private static boolean isOrigin(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0;
    }

    @Override
    public Block getBlock(int p_147439_1_, int p_147439_2_, int p_147439_3_) {
        if (isOrigin(p_147439_1_, p_147439_2_, p_147439_3_)) {
            return block;
        } else {
            return Blocks.air;
        }
    }

    // Only at the block itself. Answering everywhere made connected texture stuff like AE2 crafting CPUs think they
    // were surrounded by copies of themselves
    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return isOrigin(x, y, z) ? tileEntity : null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int p_72802_4_) {
        int blockLight = 15;
        int skyLight = 15;
        return (skyLight << 20) | (blockLight << 4);
    }

    @Override
    public int getBlockMetadata(int p_72805_1_, int p_72805_2_, int p_72805_3_) {
        return metadata;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int directionIn) {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return !isOrigin(x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return BiomeGenBase.plains;
    }

    @Override
    public int getHeight() {
        return 255;
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        return block != null && !block.isAir(this, x, y, z);
    }
}
