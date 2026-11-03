package com.colen.tempora.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class RenderingErrorBlock extends Block {

    private IIcon icon;

    public RenderingErrorBlock() {
        super(Material.rock);
        setBlockName("rendering_error_block");
        setBlockTextureName("tempora:rendering_error");
        setCreativeTab(null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.icon = reg.registerIcon(getTextureName());
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return this.icon;
    }
}
