package com.colen.tempora.rendering.regions;

import static com.colen.tempora.rendering.RenderUtils.correctForCamera;

import com.colen.tempora.rendering.ClientRegionStore;
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

    public static final long RENDER_DURATION_SECONDS = 20;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        GL11.glPushMatrix();
        correctForCamera(e);

        for (RegionToRender r : ClientRegionStore.all()) {
            if (r.dim != curDim) continue;

            double eps = RegionToRender.BLOCK_EDGE_EPSILON;

            RenderUtils.renderBoundingBox(
                r.minX + eps,
                r.minY + eps,
                r.minZ + eps,
                r.maxX - eps,
                r.maxY - eps,
                r.maxZ - eps
            );

            if (r.renderMode == RegionRenderMode.BLOCK_CHANGE) {
                float[] rgb = r.color.getRGBColorComponents(null);
                GL11.glColor3f(rgb[0], rgb[1], rgb[2]);

                RenderUtils.renderRegion(
                    r.minX + eps,
                    r.minY + eps,
                    r.minZ + eps,
                    r.maxX - eps,
                    r.maxY - eps,
                    r.maxZ - eps,
                    rgb[0], rgb[1], rgb[2]
                );
            }
        }

        GL11.glPopMatrix();

        long cutoff = System.currentTimeMillis() - RENDER_DURATION_SECONDS * 1000L;
        ClientRegionStore.expire(cutoff);
    }
}
