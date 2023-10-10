package com.myname.mymodid.Commands.HeatMap;

import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

import static com.myname.mymodid.Rendering.RenderUtils.addRenderedBlockInWorld;


public class HeatMapRenderer {

    public static ArrayList<HeatMapPacketHandler.PlayerPostion> tasks = new ArrayList<>();

    public static void renderInWorld(RenderWorldLastEvent event) {

        GL11.glPushMatrix();
        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;
        GL11.glTranslated(-playerX, -playerY, -playerZ);  // Translate the camera to the correct position in the world

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        //GL11.glTranslated(0.5, 0.5, 0.5);

        // Draw actual blocks
        for (HeatMapPacketHandler.PlayerPostion postion : tasks) {
            GL11.glPushMatrix();
            GL11.glColor4f(1.0F, 0.0F, 0.0F, (float) postion.getIntensity());
            addRenderedBlockInWorld(Blocks.stone, 0, postion.getX(), postion.getY(), postion.getZ());
            GL11.glPopMatrix();
        }

        tessellator.draw();

        GL11.glPopMatrix();
    }
}
