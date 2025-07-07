package com.colen.tempora.rendering;

import com.colen.tempora.networking.PacketDetectedInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderEventsInWorld {

    private static final double SCALE_FACTOR = 0.9;
    private static final double SECONDS_RENDERING_DURATION = 10;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (PacketDetectedInfo.CLIENT_POS.isEmpty()) return;

        Tessellator tes = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();

        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        int curDim = mc.thePlayer.dimension;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 0.5f);

        for (PacketDetectedInfo.Pos pos : PacketDetectedInfo.CLIENT_POS) {
            if (pos.dim != curDim) continue;

            GL11.glPushMatrix();
            GL11.glTranslated(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);

            tes.startDrawingQuads();
            RenderUtils.addRenderedBlockInWorld(Blocks.cobblestone, 0, 0, 0, 0);
            tes.draw();

            GL11.glPopMatrix();
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();


        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        PacketDetectedInfo.CLIENT_POS.removeIf(pos -> pos.posPrintTime < expiryCutoff);

    }
}
