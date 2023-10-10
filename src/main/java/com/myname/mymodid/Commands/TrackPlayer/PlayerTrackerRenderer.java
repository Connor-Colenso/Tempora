package com.myname.mymodid.Commands.TrackPlayer;

import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PlayerTrackerRenderer {

    public static PriorityQueue<PlayerPositionPacketHandler.PlayerPosition> tasks = new PriorityQueue<>(Comparator.comparingLong(task -> task.time));

    public static void clearBuffer() {
        tasks.clear();
    }

    public static void renderInWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

        List<PlayerPositionPacketHandler.PlayerPosition> tempList = new ArrayList<>();

        // Retrieve positions in their priority order
        while (!tasks.isEmpty()) {
            tempList.add(tasks.poll());
        }

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);  // Translate to the correct position in the world

        renderLinesConnectingPositions(tempList);
        //renderBlocksAtPositions(tempList);

        GL11.glPopMatrix();

        // Re-insert positions back into the queue
        tasks.addAll(tempList);
    }

    private static void renderLinesConnectingPositions(List<PlayerPositionPacketHandler.PlayerPosition> positions) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(3.0F);

        GL11.glBegin(GL11.GL_LINE_STRIP);

        int size = positions.size();
        for (int i = 0; i < size; i++) {
            PlayerPositionPacketHandler.PlayerPosition position = positions.get(i);

            // Calculate color based on the index.
            float ratio = (float) i / (float) size;
            float red = 1.0f - ratio;

            GL11.glColor4f(red, ratio, 0.0F, 1.0F);
            GL11.glVertex3d(position.x, position.y + 1, position.z);
        }

        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

}
