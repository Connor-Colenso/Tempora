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
public final class LastWorldEventRenderer {

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (PacketDetectedInfo.CLIENT_POS.isEmpty()) return;

        Tessellator tessellator = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        int curDim = mc.thePlayer.dimension;

        GL11.glPushMatrix();

        // Translate world so player is at the origin (view transform)
        GL11.glTranslated(-playerX, -playerY, -playerZ);

        for (PacketDetectedInfo.Pos pos : PacketDetectedInfo.CLIENT_POS) {
            if (pos.dim != curDim) continue;

            GL11.glPushMatrix();
            GL11.glTranslated(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);

            tessellator.startDrawingQuads();
            RenderUtils.addRenderedBlockInWorld(Blocks.cobblestone, 0, 0, 0, 0);
            tessellator.draw();

            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
    }

}
