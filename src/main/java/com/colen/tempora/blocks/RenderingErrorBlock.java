package com.colen.tempora.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

public class RenderingErrorBlock extends Block {

    private IIcon icon;

    public RenderingErrorBlock() {
        super(Material.rock);
        setBlockName("renderingErrorBlock");
        setBlockTextureName("tempora:rendering_error");
        setCreativeTab(null);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.icon = reg.registerIcon(getTextureName());
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return this.icon;
    }
}
