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
import net.minecraft.entity.EntityLivingBase;
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

public abstract class RenderUtils {

    public static void renderEntityInWorld(Entity entity, double x, double y, double z, float rotationYaw, float rotationPitch) {
        if (entity == null) return;

        RenderManager rm = RenderManager.instance;
        entity.setPosition(x, y, z);

        GL11.glPushMatrix();
        GL11.glColor3d(1, 1, 1);
        GL11.glTranslated(x - RenderManager.renderPosX, y - RenderManager.renderPosY, z - RenderManager.renderPosZ);

        // Set all rotation and render-related fields, if present
        entity.prevRotationYaw = entity.rotationYaw = rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch = rotationPitch;

        // For living mobs, update their rotation head/yaw offsets
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.renderYawOffset = living.prevRenderYawOffset = rotationYaw;
            living.rotationYawHead = living.prevRotationYawHead = rotationYaw;
        }

        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;

        // Force full-bright
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        // Use partialTicks = 1f to get the exact set rotation
        rm.renderEntityWithPosYaw(entity, 0.0D, 0.0D, 0.0D, rotationYaw, 1.0F);

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);
        GL11.glEnable(GL11.GL_LIGHTING);

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

    public static List<GenericQueueElement> getSortedLatestEventsByDistance(
        Collection<GenericQueueElement> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, GenericQueueElement> latestPerBlock = new HashMap<>();

        for (GenericQueueElement element : input) {
            if (element.dimensionId != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;
            GenericQueueElement existing = latestPerBlock.get(key);

            if (existing == null || element.timestamp > existing.timestamp) {
                latestPerBlock.put(key, element);
            }
        }

        List<GenericQueueElement> sorted = new ArrayList<>(latestPerBlock.values());
        sortByDistanceDescending(sorted, e);
        return sorted;
    }

    public static void sortByDistanceDescending(List<GenericQueueElement> list, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        list.sort((a, b) -> Double.compare(squaredDistance(b, px, py, pz), squaredDistance(a, px, py, pz)));
    }

    private static double squaredDistance(GenericQueueElement e, double x, double y, double z) {
        double dx = e.x - x;
        double dy = e.y - y;
        double dz = e.z - z;
        return dx * dx + dy * dy + dz * dz;
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
