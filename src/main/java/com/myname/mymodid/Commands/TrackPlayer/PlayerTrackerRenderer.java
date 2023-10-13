package com.myname.mymodid.Commands.TrackPlayer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;

public class PlayerTrackerRenderer {

    public static List<PlayerPositionPacketHandler.PlayerPosition> tasks = new ArrayList<>();

    public static void clearBuffer() {
        tasks.clear();
    }

    public static void renderInWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX
            + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY
            + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ
            + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ); // Translate to the correct position in the world

        renderLinesConnectingPositions();

        GL11.glPopMatrix();
    }

    private static void renderLinesConnectingPositions() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(3.0F);

        GL11.glBegin(GL11.GL_LINE_STRIP);

        int size = tasks.size();
        float sizeFloat = (float) size;

        for (int i = 0; i < size; i++) {
            PlayerPositionPacketHandler.PlayerPosition position = tasks.get(i);

            // Calculate color based on the index.
            float ratio = (float) i / sizeFloat;
            float red = 1.0f - ratio;

            GL11.glColor4f(red, ratio, 0.0F, 1.0F);
            GL11.glVertex3d(position.x, position.y + 1, position.z);
        }

        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
