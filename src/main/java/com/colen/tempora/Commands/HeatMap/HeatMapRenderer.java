package com.colen.tempora.Commands.HeatMap;

import static com.colen.tempora.Rendering.RenderUtils.addRenderedBlockInWorld;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.Commands.HeatMap.Network.HeatMapPacketHandler;

public class HeatMapRenderer {

    public static ArrayList<HeatMapPacketHandler.PlayerPostion> tasks = new ArrayList<>();
    private static final double SCALE_FACTOR = 0.8;

    public static void renderInWorld(RenderWorldLastEvent event) {
        GL11.glPushMatrix();

        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX
            + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY
            + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ
            + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;
        GL11.glTranslated(-playerX, -playerY, -playerZ);

        Tessellator tessellator = Tessellator.instance;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Sort tasks based on distance from player.
        // We must do this each render frame because the player may move.
        tasks.sort((HeatMapPacketHandler.PlayerPostion a, HeatMapPacketHandler.PlayerPostion b) -> {
            double da = Math.pow(a.x - playerX, 2) + Math.pow(a.y - playerY, 2) + Math.pow(a.z - playerZ, 2);
            double db = Math.pow(b.x - playerX, 2) + Math.pow(b.y - playerY, 2) + Math.pow(b.z - playerZ, 2);
            return Double.compare(db, da); // Sort in descending order so the furthest blocks are rendered first.
        });

        for (HeatMapPacketHandler.PlayerPostion position : tasks) {
            GL11.glPushMatrix();

            double blockX = position.x;
            double blockY = position.y;
            double blockZ = position.z;

            // Translate to the position
            GL11.glTranslated(blockX, blockY, blockZ);

            // Translate by half of the block, scale, and translate back
            GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);
            GL11.glTranslated(-0.5 / SCALE_FACTOR, 0.5 / SCALE_FACTOR, -0.5 / SCALE_FACTOR);

            GL11.glColor4f(1.0F, 0.0F, 0.0F, (float) Math.max(position.intensity, 0.3));

            tessellator.startDrawingQuads();
            addRenderedBlockInWorld(Blocks.stone, 0, 0, 0, 0);
            tessellator.draw();

            GL11.glPopMatrix();
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

}
