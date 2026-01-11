package com.colen.tempora.rendering;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.loggers.block_change.region_registry.RenderRegionAlternatingCheckers;
import com.colen.tempora.networking.PacketShowRegionInWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.awt.Color;


@SideOnly(Side.CLIENT)
public final class RenderRegionsInWorld {

    public static final long SECONDS_RENDERING_DURATION = 10;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        for (RenderRegionAlternatingCheckers r : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (r.dim != curDim) continue;

            // Limit render range.
//            double centerX = (r.minX + r.maxX) / 2.0;
//            double centerY = (r.minY + r.maxY) / 2.0;
//            double centerZ = (r.minZ + r.maxZ) / 2.0;

//            if (Minecraft.getMinecraft().thePlayer.getDistanceSq(centerX, centerY, centerZ) > 128 * 128) continue; // 512 blocks max render distance

            // todo range limits
            RenderUtils.renderCheckerDebugCuboidWithRange(e, r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, 5.0);

            BlockChangeLogger blockChangeLogger = (BlockChangeLogger) TemporaLoggerManager.getLogger(TemporaEvents.BLOCK_CHANGE);
            Color color = blockChangeLogger.getColour();
            RenderUtils.renderRegion(r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, color.getRed()/256.0, color.getGreen()/256.0, color.getBlue()/256.0);
        }

        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        PacketShowRegionInWorld.CLIENT_REGIONS.removeIf(intRegion -> intRegion.posPrintTime < expiryCutoff);
    }

}
