package com.colen.tempora.rendering;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public abstract class RenderUtils {

    static final double[] BLOCK_X = { -0.5, -0.5, +0.5, +0.5, +0.5, +0.5, -0.5, -0.5 };
    static final double[] BLOCK_Y = { +0.5, -0.5, -0.5, +0.5, +0.5, -0.5, -0.5, +0.5 };
    static final double[] BLOCK_Z = { +0.5, +0.5, +0.5, +0.5, -0.5, -0.5, -0.5, -0.5 };

    public static void renderBlockInWorld(RenderWorldLastEvent e, double x, double y, double z, int blockID, int metadata) {

        Tessellator tes = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();

        double px = mc.thePlayer.lastTickPosX
            + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY
            + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ
            + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        int curDim = mc.thePlayer.dimension;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 0.5f);

        GL11.glPushMatrix();
        GL11.glTranslated(
            x + 0.5,
            y + 0.5,
            z + 0.5);
        double SCALE_FACTOR = 0.8;
        GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);

        tes.startDrawingQuads();
        RenderUtils.drawBlock(
            Block.getBlockById(blockID),
            metadata,
            0,
            0,
            0);
        tes.draw();

        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void drawBlock(final Block block, final int meta, final double x, final double y,
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
