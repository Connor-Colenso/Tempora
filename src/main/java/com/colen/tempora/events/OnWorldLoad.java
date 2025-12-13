package com.colen.tempora.events;

import net.minecraftforge.event.world.WorldEvent;

import com.colen.tempora.loggers.generic.ModpackVersionData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class OnWorldLoad {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (e.world.isRemote) return;
        if (e.world.provider.dimensionId != 0) return;

        ModpackVersionData.init();
    }
}
