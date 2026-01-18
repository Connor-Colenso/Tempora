package com.colen.tempora.events;

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
        if (!DebugConfig.randomisedRegionGeneratorBlockChangeDebugger) return;

        MinecraftServer server = MinecraftServer.getServer();
        World world = server.worldServerForDimension(0);

        DebugUtils.randomizeRegion(world, 10, 10, 10, 20, 20, 20);
    }
}
