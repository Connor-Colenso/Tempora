package com.colen.tempora.events;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.colen.tempora.config.DebugConfig;
import com.colen.tempora.utils.DebugUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

//        // Wait on event queues if one is too large. Works for now.
//        try {
//            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
//                while (logger.shouldStall()) {
//                    Tempora.LOG.warn(
//                        "Event queue for {} is critically large, server stalled to process backlog ({} events pending).",
//                        logger.getLoggerName(),
//                        logger.getQueueSize()
//                    );
//
//                    PlayerUtils.sendMessageToOps("tempora.op.warning.queue.too.large", logger.getLoggerName(), new ChatComponentNumber(logger.getQueueSize()));
//
//                    // Time how long until queue is empty.
//                    long start = System.nanoTime();
//                    while (logger.getQueueSize() != 0) {
//                        Thread.sleep(100); // Not ideal.
//                    }
//                    long end = System.nanoTime();
//                    long durationMs = (end - start) / 1_000_000;
//
//                    Tempora.LOG.warn(
//                        "Event queue for {} now has {} events pending after server slowdown. Took {}ms.",
//                        logger.getLoggerName(),
//                        logger.getQueueSize(),
//                        durationMs
//                    );
//                    PlayerUtils.sendMessageToOps("tempora.warning.server.slowdown", logger.getLoggerName());
//                }
//            }
//        } catch (InterruptedException e) {
//            Tempora.LOG.error("Interrupted while sleeping ServerTickHandler", e);
//        }

        // Debug generate random cube.
        if (!DebugConfig.randomisedRegionGeneratorBlockChangeDebugger) return;

        MinecraftServer server = MinecraftServer.getServer();
        World world = server.worldServerForDimension(0);

        DebugUtils.randomizeRegion(world, 10, 10, 10, 20, 20, 20);
    }
}
