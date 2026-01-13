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

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        int curDim = Minecraft.getMinecraft().thePlayer.dimension;

        GL11.glPushMatrix();
        correctForCamera(e);

        for (RegionToRender r : ClientRegionStore.all()) {
            if (r.getDimID() != curDim) continue;

            double eps = RegionToRender.BLOCK_EDGE_EPSILON;

            RenderUtils.renderBoundingBox(
                r.getMinX() + eps,
                r.getMinY() + eps,
                r.getMinZ() + eps,
                r.getMaxX() - eps,
                r.getMaxY() - eps,
                r.getMaxZ() - eps
            );

            if (r.getRenderMode() == RegionRenderMode.BLOCK_CHANGE) {
                float[] rgb = r.getColor().getRGBColorComponents(null);
                GL11.glColor3f(rgb[0], rgb[1], rgb[2]);

                RenderUtils.renderRegion(
                    r.getMinX() + eps,
                    r.getMinY() + eps,
                    r.getMinZ() + eps,
                    r.getMaxX() - eps,
                    r.getMaxY() - eps,
                    r.getMaxZ() - eps,
                    rgb[0], rgb[1], rgb[2]
                );
            }
        }

        GL11.glPopMatrix();

        ClientRegionStore.expire();
    }
}
