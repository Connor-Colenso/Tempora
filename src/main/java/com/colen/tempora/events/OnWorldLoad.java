package com.colen.tempora.events;

import net.minecraftforge.event.world.WorldEvent;

import com.colen.tempora.loggers.generic.ModpackVersionData;
import com.colen.tempora.utils.GenericUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class OnWorldLoad {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (e.world.isRemote) return;
        if (e.world.provider.dimensionId != 0) return;
        // Saved with the world, so worlds Tempora never ran in don't get it
        if (!GenericUtils.shouldTemporaRun()) return;

        ModpackVersionData.init();
    }
}
