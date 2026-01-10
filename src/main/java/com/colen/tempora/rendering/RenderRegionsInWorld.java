package com.colen.tempora.rendering;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.loggers.block_change.region_registry.RenderRegionAlternatingCheckers;
import com.colen.tempora.networking.PacketShowRegionInWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderRegionsInWorld {

    public static final long SECONDS_RENDERING_DURATION = 10;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        for (RenderRegionAlternatingCheckers r : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (r.dim != curDim) continue;
            // todo range limits
            RenderUtils.renderCheckerDebugCuboid(e, r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ);
        }

        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        PacketShowRegionInWorld.CLIENT_REGIONS.removeIf(intRegion -> intRegion.posPrintTime < expiryCutoff);
    }

}
