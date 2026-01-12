package com.colen.tempora.rendering.regions;

import static com.colen.tempora.networking.PacketShowRegionInWorld.CLIENT_BLOCK_CHANGE_REGIONS;
import static com.colen.tempora.rendering.RenderUtils.correctForCamera;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderRegionsInWorld {

    // todo config + per "channel" regions, for more fine control.
    // Tempora wand etc must use differing renderer eventually.
    public static final long SECONDS_RENDERING_DURATION = 20;
    public static final double epsi = 0.002;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        for (RegionToRender r : CLIENT_BLOCK_CHANGE_REGIONS) {
            if (r.dim != curDim) continue;
            GL11.glPushMatrix();
            correctForCamera(e);

            // todo range limits
            RenderUtils.renderBoundingBox(
                r.minX + epsi,
                r.minY + epsi,
                r.minZ + epsi,
                r.maxX - epsi,
                r.maxY - epsi,
                r.maxZ - epsi);

            // Color region in.
            float[] rgb = r.color.getRGBColorComponents(null);
            GL11.glColor3f(rgb[0], rgb[1], rgb[2]);

            RenderUtils.renderRegion(
                r.minX + epsi,
                r.minY + epsi,
                r.minZ + epsi,
                r.maxX - epsi,
                r.maxY - epsi,
                r.maxZ - epsi,
                rgb[0],
                rgb[1],
                rgb[2]);

            GL11.glPopMatrix();
        }

        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        CLIENT_BLOCK_CHANGE_REGIONS.removeIf(intRegion -> intRegion.posPrintTime < expiryCutoff);
    }

}
