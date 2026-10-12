package com.colen.tempora.loggers.player_block_place;

import java.awt.Color;

import com.colen.tempora.rendering.events.BlockEventRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows each placed block where it was placed. */
@SideOnly(Side.CLIENT)
public class PlayerBlockPlaceRenderer extends BlockEventRenderer<PlayerBlockPlaceEventInfo> {

    public PlayerBlockPlaceRenderer() {
        super(Color.GREEN);
    }

    @Override
    protected int blockID(PlayerBlockPlaceEventInfo event) {
        return event.blockID;
    }

    @Override
    protected int metadata(PlayerBlockPlaceEventInfo event) {
        return event.metadata;
    }

    @Override
    protected String encodedNBT(PlayerBlockPlaceEventInfo event) {
        return event.encodedNBT;
    }
}
