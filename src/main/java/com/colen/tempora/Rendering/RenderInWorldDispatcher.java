package com.colen.tempora.Rendering;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.Commands.HeatMap.HeatMapRenderer;
import com.colen.tempora.Commands.TrackPlayer.PlayerTrackerRenderer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class RenderInWorldDispatcher {

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PlayerTrackerRenderer.renderInWorld(event);
        HeatMapRenderer.renderInWorld(event);
    }
}
