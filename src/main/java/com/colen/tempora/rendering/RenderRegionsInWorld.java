package com.colen.tempora.rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.loggers.block_change.IntRegion;
import com.colen.tempora.networking.PacketShowRegionInWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Thin white outline for every synced region (no filled faces). */
@SideOnly(Side.CLIENT)
public final class RenderRegionsInWorld {

    public static final long SECONDS_RENDERING_DURATION = 10;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;
        int curDim = mc.thePlayer.dimension;

        for (IntRegion r : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (r.dim != curDim) continue;
            GL11.glPushMatrix();
            GL11.glTranslated(-px, -py, -pz);

            renderRegion(r.minX, r.minY, r.minZ, r.maxX + 1, r.maxY + 1, r.maxZ + 1);

            GL11.glPopMatrix();
        }

        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        PacketShowRegionInWorld.CLIENT_REGIONS.removeIf(intRegion -> intRegion.posPrintTime < expiryCutoff);
    }

    private void renderRegion(double startX, double startY, double startZ,
                              double endX, double endY, double endZ) {

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2F);
        GL11.glColor4f(1F, 0F, 0F, 0.7F);

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(startX, startY, startZ, endX, endY, endZ);
        RenderGlobal.drawOutlinedBoundingBox(bb, 0xFFFFFFFF);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

}
