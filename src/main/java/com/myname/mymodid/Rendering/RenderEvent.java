package com.myname.mymodid.Rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class RenderEvent {

    public static final PriorityQueue<PlayerPosition> tasks = new PriorityQueue<>(Comparator.comparingDouble(task -> task.time));

    public static void clearBuffer() {
        tasks.clear();
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

        List<PlayerPosition> tempList = new ArrayList<>();

        // Retrieve positions in their priority order
        while (!tasks.isEmpty()) {
            tempList.add(tasks.poll());
        }

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);  // Translate to the correct position in the world

        renderLinesConnectingPositions(tempList);
        renderBlocksAtPositions(tempList);

        GL11.glPopMatrix();

        // Re-insert positions back into the queue
        tasks.addAll(tempList);
    }

    private void renderLinesConnectingPositions(List<PlayerPosition> positions) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(3.0F);

        GL11.glBegin(GL11.GL_LINE_STRIP);

        int size = positions.size();
        for (int i = 0; i < size; i++) {
            PlayerPosition position = positions.get(i);

            // Calculate color based on the index.
            float ratio = (float) i / (float) size;
            float red = 1.0f - ratio;

            GL11.glColor4f(red, ratio, 0.0F, 1.0F);
            GL11.glVertex3d(position.x, position.y + 1f, position.z);
        }

        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    static final double[] BLOCK_X = { -0.5, -0.5, +0.5, +0.5, +0.5, +0.5, -0.5, -0.5 };
    static final double[] BLOCK_Y = { +0.5, -0.5, -0.5, +0.5, +0.5, -0.5, -0.5, +0.5 };
    static final double[] BLOCK_Z = { +0.5, +0.5, +0.5, +0.5, -0.5, -0.5, -0.5, -0.5 };


    private void renderBlocksAtPositions(List<PlayerPosition> positions) {
        Tessellator.instance.startDrawingQuads();
        for (PlayerPosition position : positions) {
            addRenderedBlockInWorld(Blocks.stone, 0, position.x, position.y + 1f
                , position.z);
        }
        Tessellator.instance.draw();
    }

    public static void addRenderedBlockInWorld(final Block block, final int meta, final double x, final double y,
                                               final double z) {
        final Tessellator tes = Tessellator.instance;
        IIcon texture;
        double minU;
        double maxU;
        double minV;
        double maxV;

        for (int side = 0; side < 6; side++) {
            texture = block.getIcon(side, meta);
            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            switch (side) {
                case 0 -> { // Bottom
                    tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], minU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], minU, minV);
                }
                case 1 -> { // Top
                    tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], minU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], minU, maxV);
                }
                case 2 -> { // North
                    tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], minU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], minU, maxV);
                }
                case 3 -> { // South
                    tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], minU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], minU, maxV);
                }
                case 4 -> { // West
                    tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], minU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], minU, maxV);
                }
                case 5 -> { // East
                    tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], maxU, maxV);
                    tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], maxU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], minU, minV);
                    tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], minU, maxV);
                }
            }
        }
    }
}
