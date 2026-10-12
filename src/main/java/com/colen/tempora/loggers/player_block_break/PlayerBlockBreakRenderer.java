package com.colen.tempora.loggers.player_block_break;

import java.awt.Color;

import com.colen.tempora.rendering.events.BlockEventRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows each broken block where it was. */
@SideOnly(Side.CLIENT)
public class PlayerBlockBreakRenderer extends BlockEventRenderer<PlayerBlockBreakEventInfo> {

    public PlayerBlockBreakRenderer() {
        super(Color.RED);
    }

    @Override
    protected int blockID(PlayerBlockBreakEventInfo event) {
        return event.blockID;
    }

    @Override
    protected int metadata(PlayerBlockBreakEventInfo event) {
        return event.metadata;
    }

    @Override
    protected String encodedNBT(PlayerBlockBreakEventInfo event) {
        return event.encodedNBT;
    }
}
