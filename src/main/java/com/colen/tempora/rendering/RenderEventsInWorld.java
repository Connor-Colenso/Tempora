package com.colen.tempora.rendering;

import com.colen.tempora.TemporaLoggerManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderEventsInWorld {

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        for (GenericPositionalLogger<?> positionalLogger : TemporaLoggerManager.getLoggerList()) {
            positionalLogger.renderEventsInWorld(e);
            positionalLogger.clearOldEventsToRender();
        }
    }
}
