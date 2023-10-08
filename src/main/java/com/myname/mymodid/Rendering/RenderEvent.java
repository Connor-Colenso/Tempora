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

    private static final Comparator<PlayerPosition> comparator = new Comparator<PlayerPosition>() {
        @Override
        public int compare(PlayerPosition task1, PlayerPosition task2) {
            return Double.compare(task1.time, task2.time);
        }
    };

    public static final PriorityQueue<PlayerPosition> tasks = new PriorityQueue<>(comparator);

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

        // Render positions
        Tessellator.instance.startDrawingQuads();
        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);  // Translate to the correct position in the world

        for (PlayerPosition position : tempList) {
            // Render the block at the specified position
            addRenderedBlockInWorld(Blocks.stone, 0, position.x, position.y, position.z);
        }
        Tessellator.instance.draw();
        GL11.glPopMatrix();

        // Re-insert positions back into the queue
        tasks.addAll(tempList);
    }


    static final double[] BLOCK_X = { -0.5, -0.5, +0.5, +0.5, +0.5, +0.5, -0.5, -0.5 };
    static final double[] BLOCK_Y = { +0.5, -0.5, -0.5, +0.5, +0.5, -0.5, -0.5, +0.5 };
    static final double[] BLOCK_Z = { +0.5, +0.5, +0.5, +0.5, -0.5, -0.5, -0.5, -0.5 };

    public static void addRenderedBlockInWorld(final Block block, final int meta, final double x, final double y,
                                               final double z) {
        final Tessellator tes = Tessellator.instance;

        IIcon texture;

        double minU;
        double maxU;
        double minV;
        double maxV;

        {
            texture = block.getIcon(4, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], minU, minV);
            tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], minU, maxV);
        }

        {
            // Bottom face.
            texture = block.getIcon(0, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], minU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], minU, minV);
        }

        {
            texture = block.getIcon(2, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[6], y + BLOCK_Y[6], z + BLOCK_Z[6], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], minU, minV);
            tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], minU, maxV);
        }

        {
            texture = block.getIcon(5, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[5], y + BLOCK_Y[5], z + BLOCK_Z[5], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], minU, minV);
            tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], minU, maxV);
        }

        {
            texture = block.getIcon(1, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[4], y + BLOCK_Y[4], z + BLOCK_Z[4], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[7], y + BLOCK_Y[7], z + BLOCK_Z[7], minU, minV);
            tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], minU, maxV);
        }

        {
            texture = block.getIcon(3, meta);

            minU = texture.getMinU();
            maxU = texture.getMaxU();
            minV = texture.getMinV();
            maxV = texture.getMaxV();

            tes.addVertexWithUV(x + BLOCK_X[2], y + BLOCK_Y[2], z + BLOCK_Z[2], maxU, maxV);
            tes.addVertexWithUV(x + BLOCK_X[3], y + BLOCK_Y[3], z + BLOCK_Z[3], maxU, minV);
            tes.addVertexWithUV(x + BLOCK_X[0], y + BLOCK_Y[0], z + BLOCK_Z[0], minU, minV);
            tes.addVertexWithUV(x + BLOCK_X[1], y + BLOCK_Y[1], z + BLOCK_Z[1], minU, maxV);
        }
    }
}
