package com.colen.tempora.events;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.NameRequests;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        TemporaEvents.blockChangeLogger.onTickEnd();
        TemporaQueries.onServerTick();
        NameRequests.onServerTick();
        GenericLogger.announceFailures();
    }
}
