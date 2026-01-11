package com.colen.tempora.rendering;

import static com.colen.tempora.rendering.RenderUtils.correctForCamera;

import java.awt.Color;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.block_change.region_registry.RenderRegionAlternatingCheckers;
import com.colen.tempora.networking.PacketShowRegionInWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderRegionsInWorld {

    // todo config + per "channel" regions, for more fine control.
    // Tempora wand etc must use differing renderer eventually.
    public static final long SECONDS_RENDERING_DURATION = 20;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        for (RenderRegionAlternatingCheckers r : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (r.dim != curDim) continue;
            GL11.glPushMatrix();
            correctForCamera(e);

            // todo range limits
            RenderUtils.renderCheckerDebugCuboidWithRange(e, r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, 64);

            double volume = (r.maxX - r.minX) * (r.maxY - r.minY) * (r.maxZ - r.minZ);

            BlockChangeLogger blockChangeLogger = (BlockChangeLogger) TemporaLoggerManager
                .getLogger(TemporaEvents.BLOCK_CHANGE);
            Color color = blockChangeLogger.getColour();

            RenderUtils.renderRegion(
                r.minX,
                r.minY,
                r.minZ,
                r.maxX,
                r.maxY,
                r.maxZ,
                color.getRed() / 256.0,
                color.getGreen() / 256.0,
                color.getBlue() / 256.0);

            GL11.glPopMatrix();
        }


        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        PacketShowRegionInWorld.CLIENT_REGIONS.removeIf(intRegion -> intRegion.posPrintTime < expiryCutoff);
    }

}
