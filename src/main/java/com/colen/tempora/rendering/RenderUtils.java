package com.colen.tempora.rendering;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.FakeWorld.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.colen.tempora.rendering.RenderRegionsInWorld.SECONDS_RENDERING_DURATION;

public abstract class RenderUtils {

    public static void renderEntityInWorld(Entity entity, double x, double y, double z, float rotationYaw, float rotationPitch) {
        if (entity == null) return;

        RenderManager rm = RenderManager.instance;
        entity.setPosition(x, y, z);

        GL11.glPushMatrix();
        GL11.glColor3d(1, 1, 1);
        GL11.glTranslated(x - rm.renderPosX, y - rm.renderPosY, z - rm.renderPosZ);

        // === Force full-bright (lightmap to 240) ===
        GL11.glDisable(GL11.GL_LIGHTING);
        rm.renderEntityWithPosYaw(entity, 0.0D, 0.0D, 0.0D, rotationYaw, rotationPitch);
        GL11.glEnable(GL11.GL_LIGHTING);

        // (Optional: Reset lightmap if needed for later rendering, not always necessary in MC rendering.)
        GL11.glPopMatrix();
    }


    public static void renderEntityAABBInWorld(Entity entity, double x, double y, double z, double red, double green, double blue) {
        if (entity == null) return;

        AxisAlignedBB aabb = entity.boundingBox;

        RenderManager rm = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x - rm.renderPosX, y - rm.renderPosY, z - rm.renderPosZ);

        // Draw bounding box relative to the position
        renderRegion(
            aabb.minX - x, aabb.minY - y, aabb.minZ - z,
            aabb.maxX - x, aabb.maxY - y, aabb.maxZ - z,
            red, green, blue
        );

        GL11.glPopMatrix();
    }


    public static void renderBlockInWorld(RenderWorldLastEvent e, double x, double y, double z, int blockID, int metadata, NBTTagCompound nbt, LoggerEnum loggerEnum) {
        Tessellator tes = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();

        // Interpolated player position for smooth rendering
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz); // World-relative render origin

        // Setup rendering environment
        RenderBlocks rb = new RenderBlocks();
        rb.useInventoryTint = false;

        Block block = Block.getBlockById(blockID);
        TileEntity tileEntity = null;

        if (nbt != null) {
            tileEntity = TileEntity.createAndLoadEntity(nbt);
            tileEntity.blockMetadata = metadata;
            tileEntity.blockType = block;
            tileEntity.setWorldObj(mc.theWorld);
            tileEntity.xCoord = 0;
            tileEntity.yCoord = 0;
            tileEntity.zCoord = 0;
            tileEntity.validate();
        }

        FakeWorld fakeWorld = new FakeWorld();
        fakeWorld.block = block;
        fakeWorld.tileEntity = tileEntity;
        fakeWorld.metadata = metadata;
        fakeWorld.x = (int) x;
        fakeWorld.y = (int) y;
        fakeWorld.z = (int) z;
        rb.blockAccess = fakeWorld;

        // === Render block centered at (x, y, z) ===
        GL11.glPushMatrix();

        // Apply scaling and centering transform
        double SCALE_FACTOR = 14.0 / 16.0;
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);
        GL11.glTranslated(-0.5, -0.5, -0.5);

        tes.startDrawingQuads();
        rb.renderBlockByRenderType(block, 0, 0, 0);
        tes.draw();

        if (System.currentTimeMillis() / 500 % 2 == 0) {
            if (loggerEnum == LoggerEnum.PlayerBlockBreakLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 1, 0, 0);
            } else if (loggerEnum == LoggerEnum.PlayerBlockPlaceLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 0, 1, 0);
            } else if (loggerEnum == LoggerEnum.BlockChangeLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 0, 0, 1);
            }
        }

        GL11.glPopMatrix();
        GL11.glPopMatrix();
    }

    public static List<GenericQueueElement> getSortedLatestEventsByDistance(Collection<GenericQueueElement> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, GenericQueueElement> latestEventsByPos = new HashMap<>();

        for (GenericQueueElement element : input) {
            if (element.dimensionId != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;

            GenericQueueElement existing = latestEventsByPos.get(key);
            if (existing == null || element.timestamp > existing.timestamp) {
                latestEventsByPos.put(key, element);
            }
        }

        return getSortedElementsByDistance(latestEventsByPos, e);
    }

    public static List<GenericQueueElement> getSortedElementsByDistance(
        Map<String, GenericQueueElement> latestEventsByPos,
        RenderWorldLastEvent e
    ) {
        Minecraft mc = Minecraft.getMinecraft();

        // Interpolated player position
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        // Copy values to a list once (to avoid mutating map.values())
        List<GenericQueueElement> sorted = new ArrayList<>(latestEventsByPos.values());

        // In-place sort, avoids stream/lambdas/GC overhead
        sorted.sort(new Comparator<>() {
            @Override
            public int compare(GenericQueueElement a, GenericQueueElement b) {
                double da = squareDist(a, px, py, pz);
                double db = squareDist(b, px, py, pz);
                return Double.compare(db, da); // reversed order
            }

            private double squareDist(GenericQueueElement e, double x, double y, double z) {
                double dx = e.x - x;
                double dy = e.y - y;
                double dz = e.z - z;
                return dx * dx + dy * dy + dz * dz;
            }
        });

        return sorted;
    }


    public static float getRenderAlpha(GenericQueueElement element) {
        final long fullDuration = SECONDS_RENDERING_DURATION * 1000L;
        final long halfDuration = fullDuration / 2L;
        final long elapsed = System.currentTimeMillis() - element.eventRenderCreationTime;

        if (elapsed <= halfDuration) {
            return 0.5f;
        }

        final long fadeDuration = fullDuration - halfDuration; // second half is fade
        float fadeProgress = (elapsed - halfDuration) / (float) fadeDuration;

        return Math.max(0f, 0.5f * (1f - Math.min(fadeProgress, 1f)));
    }

    public static void renderRegion(double startX, double startY, double startZ,
                                    double endX, double endY, double endZ, double red, double green, double blue) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT); // Save lighting, texture, blend, depth, etc.
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2F);
        GL11.glColor3d(red, green, blue);

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(startX, startY, startZ, endX, endY, endZ);
        RenderGlobal.drawOutlinedBoundingBox(bb, 0xFFFFFFFF);

        GL11.glPopMatrix();
        GL11.glPopAttrib(); // Restore everything
    }

}
