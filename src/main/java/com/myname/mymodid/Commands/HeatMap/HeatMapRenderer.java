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
    private static final double SCALE_FACTOR = 0.8;

    public static void renderInWorld(RenderWorldLastEvent event) {

        GL11.glPushMatrix();

        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;
        GL11.glTranslated(-playerX, -playerY, -playerZ);  // Translate the block to not follow the camera in the world.

        Tessellator tessellator = Tessellator.instance;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (HeatMapPacketHandler.PlayerPostion position : tasks) {
            GL11.glPushMatrix();

            double blockX = position.getX();
            double blockY = position.getY();
            double blockZ = position.getZ();

            // Translate to the position
            GL11.glTranslated(blockX, blockY, blockZ);

            // Translate by half of the block, scale, and translate back
            GL11.glTranslated(0.5, 0.5, 0.5);
            GL11.glScalef(0.5f, 0.5f ,0.5f);
            GL11.glTranslated(-2, 0, -2);

            GL11.glColor4f(1.0F, 0.0F, 0.0F, (float) Math.max(position.getIntensity(), 0.3));

            tessellator.startDrawingQuads();
            addRenderedBlockInWorld(Blocks.stone, 0, 0, 0, 0); // Rendering at (0,0,0) because we've translated
            tessellator.draw();

            GL11.glPopMatrix();
        }

        GL11.glDisable(GL11.GL_BLEND);

        GL11.glPopMatrix();
    }

}
