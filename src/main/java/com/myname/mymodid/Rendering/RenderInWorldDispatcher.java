package com.myname.mymodid.Rendering;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.myname.mymodid.Commands.HeatMap.HeatMapRenderer;
import com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerRenderer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class RenderInWorldDispatcher {

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PlayerTrackerRenderer.renderInWorld(event);
        HeatMapRenderer.renderInWorld(event);
    }
}
