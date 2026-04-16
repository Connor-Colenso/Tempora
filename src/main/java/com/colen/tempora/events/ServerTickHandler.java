package com.colen.tempora.events;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.colen.tempora.config.DebugConfig;
import com.colen.tempora.utils.DebugUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Wait on event queues if one is too large. Works for now.
        try {
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                while (logger.shouldStall()) {
                    Thread.sleep(500); // Not ideal.
                    Tempora.LOG.warn(
                        "Event queue for {} is critically large; server stalled to process backlog ({} events pending).",
                        logger.getLoggerName(),
                        logger.getQueueSize()
                    );
                }
            }
        } catch (InterruptedException e) {
            Tempora.LOG.error("Interrupted while sleeping ServerTickHandler", e);
        }

        // Debug generate random cube.
        if (!DebugConfig.randomisedRegionGeneratorBlockChangeDebugger) return;

        MinecraftServer server = MinecraftServer.getServer();
        World world = server.worldServerForDimension(0);

        DebugUtils.randomizeRegion(world, 10, 10, 10, 20, 20, 20);
    }
}
