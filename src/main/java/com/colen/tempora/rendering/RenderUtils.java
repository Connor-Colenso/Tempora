package com.colen.tempora.rendering;

import appeng.block.networking.BlockCableBus;
import appeng.client.render.BusRenderHelper;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.FakeWorld.FakeWorld;
import com.gtnewhorizons.modularui.api.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RenderUtils {

    public static void renderEntityInWorld(Entity entity, double x, double y, double z, float rotationYaw, float rotationPitch) {
        if (entity == null) return;

        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glColor4f(1, 1, 1, 1);

        RenderManager rm = RenderManager.instance;
        entity.setPosition(x, y, z);

        GL11.glTranslated(x - RenderManager.renderPosX, y - RenderManager.renderPosY, z - RenderManager.renderPosZ);

        entity.prevRotationYaw = entity.rotationYaw = rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch = rotationPitch;

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase)entity;
            living.renderYawOffset = living.prevRenderYawOffset = rotationYaw;
            living.rotationYawHead = living.prevRotationYawHead = rotationYaw;
        }

        // Usually, if you want fullbright for entities, uncomment these:
        // GL11.glDisable(GL11.GL_LIGHTING);
        // OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        // Otherwise, let vanilla control lighting for entities!

        rm.renderEntityWithPosYaw(entity, 0.0D, 0.0D, 0.0D, rotationYaw, 1.0F);

        // === Essential state reset: ===
        GL11.glColor4f(1, 1, 1, 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);
        GL11.glEnable(GL11.GL_LIGHTING);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }


    public static void renderEntityAABBInWorld(Entity entity, double x, double y, double z, double red, double green, double blue) {
        if (entity == null) return;

        AxisAlignedBB aabb = entity.boundingBox;

        GL11.glPushMatrix();
        GL11.glTranslated(x - RenderManager.renderPosX, y - RenderManager.renderPosY, z - RenderManager.renderPosZ);

        // Draw bounding box relative to the position
        renderRegion(
            aabb.minX - x, aabb.minY - y, aabb.minZ - z,
            aabb.maxX - x, aabb.maxY - y, aabb.maxZ - z,
            red, green, blue
        );

        GL11.glPopMatrix();
    }


    public static void renderBlockInWorld(RenderWorldLastEvent e, double x, double y, double z, int blockID, int metadata, NBTTagCompound nbt, LoggerEnum loggerEnum) {
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;

        // Save state
        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        // Calculate interpolated player position for shifting origin
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        GL11.glColor4f(1, 1, 1, 1);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        GL11.glTranslated(-px, -py, -pz);

        RenderBlocks rb = new RenderBlocks();
        rb.useInventoryTint = false;
        Block block = Block.getBlockById(blockID);

        TileEntity tileEntity = null;
        if (nbt != null) {
            tileEntity = TileEntity.createAndLoadEntity(nbt);
            tileEntity.blockMetadata = metadata;
            tileEntity.blockType = block;
            tileEntity.setWorldObj(mc.theWorld);
            tileEntity.xCoord = 0; tileEntity.yCoord = 0; tileEntity.zCoord = 0;
            tileEntity.validate();
        }

        // Fake world logic
        FakeWorld fakeWorld = new FakeWorld();
        fakeWorld.block = block;
        fakeWorld.tileEntity = tileEntity;
        fakeWorld.metadata = metadata;
        fakeWorld.x = (int) x; fakeWorld.y = (int) y; fakeWorld.z = (int) z;
        rb.blockAccess = fakeWorld;

        // AE2 CableBus hack if needed
        if (block instanceof BlockCableBus) {
            BusRenderHelper.instances.get().setPass(0);
        }

        // Begin transform
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        double SCALE_FACTOR = 14.0 / 16.0;
        GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);
        GL11.glTranslated(-0.5, -0.5, -0.5);

        // Block render
        tes.startDrawingQuads();
        rb.renderBlockByRenderType(block, 0, 0, 0);
        tes.draw();

        // Optionally render logger regions
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            if (loggerEnum == LoggerEnum.PlayerBlockBreakLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 1, 0, 0);
            } else if (loggerEnum == LoggerEnum.PlayerBlockPlaceLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 0, 1, 0);
            } else if (loggerEnum == LoggerEnum.BlockChangeLogger) {
                renderRegion(0, 0, 0, 1, 1, 1, 0, 0, 1);
            } else if (loggerEnum == LoggerEnum.ExplosionLogger) {
                // Purple.
                renderRegion(0,0,0,1,1,1,0.655, 0.125, 0.8);
            }
        }
        GL11.glPopMatrix(); // end scale/align

        GL11.glPopMatrix(); // end world-relative

        // === Absolutely necessary for correct vanilla/forge rendering: ===
        GL11.glColor4f(1, 1, 1, 1); // Always restore color
        GL13.glActiveTexture(GL13.GL_TEXTURE0); // Set main texture unit (block textures)
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture); // Bind MC block atlas
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY); // Only if you changed lightmap coords above
        GL11.glEnable(GL11.GL_LIGHTING); // Vanilla expects this on

        GL11.glPopAttrib();
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


    public static void renderFloatingText(List<String> textLines, double x, double y, double z) {
        RenderManager renderManager = RenderManager.instance;
        FontRenderer fontrenderer = RenderManager.instance.getFontRenderer();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        int fontHeight = fontrenderer.FONT_HEIGHT;
        // Start so that the overall text block is vertically centered
        int totalHeight = textLines.size() * fontHeight;
        int yOffset = -totalHeight / 2;

        for (String line : textLines) {
            fontrenderer.drawString(
                line,
                -fontrenderer.getStringWidth(line) / 2,
                yOffset,
                0xFFFFFF
            );
            yOffset += fontHeight;
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

}
