package com.colen.tempora.loggers.block_change;

import java.awt.Color;

import com.colen.tempora.rendering.events.BlockEventRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Each changed block as it was before the change
@SideOnly(Side.CLIENT)
public class BlockChangeRenderer extends BlockEventRenderer<BlockChangeEventInfo> {

    public BlockChangeRenderer() {
        super(Color.ORANGE);
    }

    @Override
    protected int blockID(BlockChangeEventInfo event) {
        return event.beforeBlockID;
    }

    @Override
    protected int metadata(BlockChangeEventInfo event) {
        return event.beforeMetadata;
    }

    @Override
    protected String encodedNBT(BlockChangeEventInfo event) {
        return event.beforeEncodedNBT;
    }
}
