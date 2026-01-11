package com.colen.tempora.rendering;

import java.awt.Color;
import java.util.List;
import java.util.function.IntUnaryOperator;

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

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.FakeWorld.FakeWorld;
import com.gtnewhorizons.modularui.api.GlStateManager;

import appeng.block.networking.BlockCableBus;
import appeng.client.render.BusRenderHelper;

public abstract class RenderUtils {

    public static void renderEntityInWorld(Entity entity, double x, double y, double z, float rotationYaw,
        float rotationPitch) {
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
            EntityLivingBase living = (EntityLivingBase) entity;
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

    public static void renderEntityAABBInWorld(Entity entity, double x, double y, double z, double red, double green,
        double blue) {
        if (entity == null) return;

        AxisAlignedBB aabb = entity.boundingBox;

        GL11.glPushMatrix();
        GL11.glTranslated(x - RenderManager.renderPosX, y - RenderManager.renderPosY, z - RenderManager.renderPosZ);

        // Draw bounding box relative to the position
        renderRegion(
            aabb.minX - x,
            aabb.minY - y,
            aabb.minZ - z,
            aabb.maxX - x,
            aabb.maxY - y,
            aabb.maxZ - z,
            red,
            green,
            blue);

        GL11.glPopMatrix();
    }

    public static void renderBlockInWorld(RenderWorldLastEvent e, double x, double y, double z, int blockID,
        int metadata, NBTTagCompound nbt, GenericPositionalLogger<?> logger) {
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;

        // Save state
        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;

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
            tileEntity.xCoord = 0;
            tileEntity.yCoord = 0;
            tileEntity.zCoord = 0;
            tileEntity.validate();
        }

        // Fake world logic
        FakeWorld fakeWorld = new FakeWorld();
        fakeWorld.block = block;
        fakeWorld.tileEntity = tileEntity;
        fakeWorld.metadata = metadata;
        fakeWorld.x = (int) x;
        fakeWorld.y = (int) y;
        fakeWorld.z = (int) z;
        rb.blockAccess = fakeWorld;

        // AE2 CableBus hack if needed
        if (block instanceof BlockCableBus) {
            BusRenderHelper.instances.get()
                .setPass(0);
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
            Color color = logger.getColour();
            renderRegion(0, 0, 0, 1, 1, 1, color.getRed(), color.getGreen(), color.getBlue());
        }

        GL11.glPopMatrix(); // end scale/align

        GL11.glPopMatrix(); // end world-relative

        // === Absolutely necessary for correct vanilla/forge rendering: ===
        GL11.glColor4f(1, 1, 1, 1); // Always restore color
        GL13.glActiveTexture(GL13.GL_TEXTURE0); // Set main texture unit (block textures)
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture); // Bind MC block atlas
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);
        GL11.glEnable(GL11.GL_LIGHTING); // Vanilla expects this on

        GL11.glPopAttrib();
    }

    public static void renderRegion(double startX, double startY, double startZ, double endX, double endY, double endZ,
        double red, double green, double blue) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT); // Save lighting, texture,
                                                                                             // blend, depth, etc.
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
        FontRenderer fontrenderer = Minecraft.getMinecraft().fontRenderer;
        if (fontrenderer == null) return;

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
            fontrenderer.drawString(line, -fontrenderer.getStringWidth(line) / 2, yOffset, 0xFFFFFF);
            yOffset += fontHeight;
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

//    /**
//     * Renders a semi-transparent checkerboard cuboid in world space.
//     *
//     * Call this from RenderWorldLastEvent (on the client render thread).
//     *
//     * @param startX min corner X (world)
//     * @param startY min corner Y (world)
//     * @param startZ min corner Z (world)
//     * @param endX   max corner X (world)
//     * @param endY   max corner Y (world)
//     * @param endZ   max corner Z (world)
//     */
//    public static void renderCheckerDebugCuboidWithRange(RenderWorldLastEvent e, double startX, double startY,
//        double startZ, double endX, double endY, double endZ, double range) {
//
//        if (range <= 0) return;
//
//        final int COLOR_A = 0x80AAAAAA;
//        final int COLOR_B = 0x80888888;
//        final double EPS = 0.005;
//
//        double minX = Math.min(startX, endX) + EPS;
//        double minY = Math.min(startY, endY) + EPS;
//        double minZ = Math.min(startZ, endZ) + EPS;
//        double maxX = Math.max(startX, endX) - EPS;
//        double maxY = Math.max(startY, endY) - EPS;
//        double maxZ = Math.max(startZ, endZ) - EPS;
//
//        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return;
//
//        final Minecraft mc = Minecraft.getMinecraft();
//        final Entity view = mc.renderViewEntity;
//        final float pt = e.partialTicks;
//
//        final double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
//        final double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
//        final double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
//
//        final double rangeSq = range * range;
//
//        // Early-out: if camera is farther than range from the cuboid AABB, no tiles can be in range.
//        double ddx = 0, ddy = 0, ddz = 0;
//        if (camX < minX) ddx = minX - camX;
//        else if (camX > maxX) ddx = camX - maxX;
//        if (camY < minY) ddy = minY - camY;
//        else if (camY > maxY) ddy = camY - maxY;
//        if (camZ < minZ) ddz = minZ - camZ;
//        else if (camZ > maxZ) ddz = camZ - maxZ;
//        if (ddx * ddx + ddy * ddy + ddz * ddz > rangeSq) return;
//
//        GL11.glPushMatrix();
//
//        GL11.glPushAttrib(
//            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
//        GL11.glDisable(GL11.GL_LIGHTING);
//        GL11.glDisable(GL11.GL_TEXTURE_2D);
//        GL11.glEnable(GL11.GL_BLEND);
//        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//        GL11.glEnable(GL11.GL_DEPTH_TEST);
//        GL11.glDepthMask(false);
//        GL11.glDisable(GL11.GL_CULL_FACE);
//
//        final Tessellator t = Tessellator.instance;
//        t.startDrawingQuads();
//
//        final int ix0 = (int) Math.floor(minX), ix1 = (int) Math.ceil(maxX);
//        final int iy0 = (int) Math.floor(minY), iy1 = (int) Math.ceil(maxY);
//        final int iz0 = (int) Math.floor(minZ), iz1 = (int) Math.ceil(maxZ);
//
//        final int faceCellXMin = (int) Math.floor(minX + 1.0e-6);
//        final int faceCellXMax = (int) Math.floor(maxX - 1.0e-6);
//        final int faceCellYMin = (int) Math.floor(minY + 1.0e-6);
//        final int faceCellYMax = (int) Math.floor(maxY - 1.0e-6);
//        final int faceCellZMin = (int) Math.floor(minZ + 1.0e-6);
//        final int faceCellZMax = (int) Math.floor(maxZ - 1.0e-6);
//
//        // -------------------------
//        // TOP (+Y) and BOTTOM (-Y)
//        // -------------------------
//        for (int xi = ix0; xi < ix1; xi++) {
//            final double xa = Math.max(minX, xi);
//            final double xb = Math.min(maxX, xi + 1.0);
//            if (xb <= xa) continue;
//
//            for (int zi = iz0; zi < iz1; zi++) {
//                final double za = Math.max(minZ, zi);
//                final double zb = Math.min(maxZ, zi + 1.0);
//                if (zb <= za) continue;
//
//                // Top quad range test (plane y = maxY)
//                double px = camX < xa ? xa : (camX > xb ? xb : camX);
//                double pz = camZ < za ? za : (camZ > zb ? zb : camZ);
//                double dx = camX - px, dy = camY - maxY, dz = camZ - pz;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (xi + faceCellYMax + zi) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(xa, maxY, za);
//                t.addVertex(xb, maxY, za);
//                t.addVertex(xb, maxY, zb);
//                t.addVertex(xa, maxY, zb);
//            }
//        }
//
//        for (int xi = ix0; xi < ix1; xi++) {
//            final double xa = Math.max(minX, xi);
//            final double xb = Math.min(maxX, xi + 1.0);
//            if (xb <= xa) continue;
//
//            for (int zi = iz0; zi < iz1; zi++) {
//                final double za = Math.max(minZ, zi);
//                final double zb = Math.min(maxZ, zi + 1.0);
//                if (zb <= za) continue;
//
//                // Bottom quad range test (plane y = minY)
//                double px = camX < xa ? xa : (Math.min(camX, xb));
//                double pz = camZ < za ? za : (Math.min(camZ, zb));
//                double dx = camX - px, dy = camY - minY, dz = camZ - pz;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (xi + faceCellYMin + zi) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(xa, minY, zb);
//                t.addVertex(xb, minY, zb);
//                t.addVertex(xb, minY, za);
//                t.addVertex(xa, minY, za);
//            }
//        }
//
//        // -------------------------
//        // +X and -X faces (YZ grid)
//        // -------------------------
//        for (int yi = iy0; yi < iy1; yi++) {
//            final double ya = Math.max(minY, yi);
//            final double yb = Math.min(maxY, yi + 1.0);
//            if (yb <= ya) continue;
//
//            for (int zi = iz0; zi < iz1; zi++) {
//                final double za = Math.max(minZ, zi);
//                final double zb = Math.min(maxZ, zi + 1.0);
//                if (zb <= za) continue;
//
//                // +X quad range test (plane x = maxX)
//                double py = camY < ya ? ya : (Math.min(camY, yb));
//                double pz = camZ < za ? za : (Math.min(camZ, zb));
//                double dx = camX - maxX, dy = camY - py, dz = camZ - pz;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (faceCellXMax + yi + zi) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(maxX, ya, za);
//                t.addVertex(maxX, ya, zb);
//                t.addVertex(maxX, yb, zb);
//                t.addVertex(maxX, yb, za);
//            }
//        }
//
//        for (int yi = iy0; yi < iy1; yi++) {
//            final double ya = Math.max(minY, yi);
//            final double yb = Math.min(maxY, yi + 1.0);
//            if (yb <= ya) continue;
//
//            for (int zi = iz0; zi < iz1; zi++) {
//                final double za = Math.max(minZ, zi);
//                final double zb = Math.min(maxZ, zi + 1.0);
//                if (zb <= za) continue;
//
//                // -X quad range test (plane x = minX)
//                double py = camY < ya ? ya : (Math.min(camY, yb));
//                double pz = camZ < za ? za : (Math.min(camZ, zb));
//                double dx = camX - minX, dy = camY - py, dz = camZ - pz;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (faceCellXMin + yi + zi) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(minX, ya, zb);
//                t.addVertex(minX, ya, za);
//                t.addVertex(minX, yb, za);
//                t.addVertex(minX, yb, zb);
//            }
//        }
//
//        // -------------------------
//        // +Z and -Z faces (XY grid)
//        // -------------------------
//        for (int xi = ix0; xi < ix1; xi++) {
//            final double xa = Math.max(minX, xi);
//            final double xb = Math.min(maxX, xi + 1.0);
//            if (xb <= xa) continue;
//
//            for (int yi = iy0; yi < iy1; yi++) {
//                final double ya = Math.max(minY, yi);
//                final double yb = Math.min(maxY, yi + 1.0);
//                if (yb <= ya) continue;
//
//                // +Z quad range test (plane z = maxZ)
//                double px = camX < xa ? xa : (Math.min(camX, xb));
//                double py = camY < ya ? ya : (Math.min(camY, yb));
//                double dx = camX - px, dy = camY - py, dz = camZ - maxZ;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (xi + yi + faceCellZMax) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(xa, ya, maxZ);
//                t.addVertex(xb, ya, maxZ);
//                t.addVertex(xb, yb, maxZ);
//                t.addVertex(xa, yb, maxZ);
//            }
//        }
//
//        for (int xi = ix0; xi < ix1; xi++) {
//            final double xa = Math.max(minX, xi);
//            final double xb = Math.min(maxX, xi + 1.0);
//            if (xb <= xa) continue;
//
//            for (int yi = iy0; yi < iy1; yi++) {
//                final double ya = Math.max(minY, yi);
//                final double yb = Math.min(maxY, yi + 1.0);
//                if (yb <= ya) continue;
//
//                // -Z quad range test (plane z = minZ)
//                double px = camX < xa ? xa : (Math.min(camX, xb));
//                double py = camY < ya ? ya : (Math.min(camY, yb));
//                double dx = camX - px, dy = camY - py, dz = camZ - minZ;
//                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
//
//                final int parity = (xi + yi + faceCellZMin) & 1;
//                final int col = (parity == 0) ? COLOR_A : COLOR_B;
//                t.setColorRGBA((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255);
//
//                t.addVertex(xb, ya, minZ);
//                t.addVertex(xa, ya, minZ);
//                t.addVertex(xa, yb, minZ);
//                t.addVertex(xb, yb, minZ);
//            }
//        }
//
//        t.draw();
//
//        GL11.glDepthMask(true);
//        GL11.glPopAttrib();
//        GL11.glPopMatrix();
//    }

    /**
     * Renders a semi-transparent checkerboard cuboid in world space.
     *
     * Optimized for ultra-large regions by only iterating tiles that can fall within
     * the given spherical "range" around the camera (projected per-face), instead
     * of iterating the entire face area.
     */
    public static void renderCheckerDebugCuboidWithRange(RenderWorldLastEvent e,
                                                         double startX, double startY, double startZ,
                                                         double endX, double endY, double endZ,
                                                         double range) {

        if (range <= 0) return;

        final int COLOR_A = 0x80AAAAAA;
        final int COLOR_B = 0x80888888;
        final int aR = (COLOR_A >> 16) & 255, aG = (COLOR_A >> 8) & 255, aB = COLOR_A & 255, aA = (COLOR_A >>> 24) & 255;
        final int bR = (COLOR_B >> 16) & 255, bG = (COLOR_B >> 8) & 255, bB = COLOR_B & 255, bA = (COLOR_B >>> 24) & 255;

        final double EPS = 0.005;

        double minX = Math.min(startX, endX) + EPS;
        double minY = Math.min(startY, endY) + EPS;
        double minZ = Math.min(startZ, endZ) + EPS;
        double maxX = Math.max(startX, endX) - EPS;
        double maxY = Math.max(startY, endY) - EPS;
        double maxZ = Math.max(startZ, endZ) - EPS;

        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return;

        final Minecraft mc = Minecraft.getMinecraft();
        final Entity view = mc.renderViewEntity;
        final float pt = e.partialTicks;

        final double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        final double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        final double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        final double rangeSq = range * range;

        // Early-out: if camera is farther than range from the cuboid AABB, no tiles can be in range.
        double ddx = 0, ddy = 0, ddz = 0;
        if (camX < minX) ddx = minX - camX;
        else if (camX > maxX) ddx = camX - maxX;
        if (camY < minY) ddy = minY - camY;
        else if (camY > maxY) ddy = camY - maxY;
        if (camZ < minZ) ddz = minZ - camZ;
        else if (camZ > maxZ) ddz = camZ - maxZ;
        if (ddx * ddx + ddy * ddy + ddz * ddz > rangeSq) return;

        final int ix0 = (int) Math.floor(minX), ix1 = (int) Math.ceil(maxX);
        final int iy0 = (int) Math.floor(minY), iy1 = (int) Math.ceil(maxY);
        final int iz0 = (int) Math.floor(minZ), iz1 = (int) Math.ceil(maxZ);

        final int faceCellXMin = (int) Math.floor(minX + 1.0e-6);
        final int faceCellXMax = (int) Math.floor(maxX - 1.0e-6);
        final int faceCellYMin = (int) Math.floor(minY + 1.0e-6);
        final int faceCellYMax = (int) Math.floor(maxY - 1.0e-6);
        final int faceCellZMin = (int) Math.floor(minZ + 1.0e-6);
        final int faceCellZMax = (int) Math.floor(maxZ - 1.0e-6);

        // small helpers
        final IntUnaryOperator clampX0 = (v) -> Math.max(ix0, Math.min(ix1, v));
        final IntUnaryOperator clampY0 = (v) -> Math.max(iy0, Math.min(iy1, v));
        final IntUnaryOperator clampZ0 = (v) -> Math.max(iz0, Math.min(iz1, v));

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        final Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        // =========================
        // TOP (+Y) and BOTTOM (-Y)
        // =========================
        {
            // TOP (y = maxY) -> iterate only XZ tiles whose distance to camera <= range
            final double dy = camY - maxY;
            final double dy2 = dy * dy;
            if (dy2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dy2);

                int xStart = clampX0.applyAsInt((int) Math.floor(camX - r));
                int xEnd   = clampX0.applyAsInt((int) Math.ceil (camX + r));
                int zStart = clampZ0.applyAsInt((int) Math.floor(camZ - r));
                int zEnd   = clampZ0.applyAsInt((int) Math.ceil (camZ + r));

                for (int xi = xStart; xi < xEnd; xi++) {
                    final double xa = Math.max(minX, xi);
                    final double xb = Math.min(maxX, xi + 1.0);
                    if (xb <= xa) continue;

                    for (int zi = zStart; zi < zEnd; zi++) {
                        final double za = Math.max(minZ, zi);
                        final double zb = Math.min(maxZ, zi + 1.0);
                        if (zb <= za) continue;

                        // exact range test (cheap now because loops are small)
                        final double px = (camX < xa) ? xa : ((camX > xb) ? xb : camX);
                        final double pz = (camZ < za) ? za : ((camZ > zb) ? zb : camZ);
                        final double dx = camX - px, dz = camZ - pz;
                        if (dx * dx + dy2 + dz * dz > rangeSq) continue;

                        final int parity = (xi + faceCellYMax + zi) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(xa, maxY, za);
                        t.addVertex(xb, maxY, za);
                        t.addVertex(xb, maxY, zb);
                        t.addVertex(xa, maxY, zb);
                    }
                }
            }
        }

        {
            // BOTTOM (y = minY)
            final double dy = camY - minY;
            final double dy2 = dy * dy;
            if (dy2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dy2);

                int xStart = clampX0.applyAsInt((int) Math.floor(camX - r));
                int xEnd   = clampX0.applyAsInt((int) Math.ceil (camX + r));
                int zStart = clampZ0.applyAsInt((int) Math.floor(camZ - r));
                int zEnd   = clampZ0.applyAsInt((int) Math.ceil (camZ + r));

                for (int xi = xStart; xi < xEnd; xi++) {
                    final double xa = Math.max(minX, xi);
                    final double xb = Math.min(maxX, xi + 1.0);
                    if (xb <= xa) continue;

                    for (int zi = zStart; zi < zEnd; zi++) {
                        final double za = Math.max(minZ, zi);
                        final double zb = Math.min(maxZ, zi + 1.0);
                        if (zb <= za) continue;

                        final double px = (camX < xa) ? xa : ((camX > xb) ? xb : camX);
                        final double pz = (camZ < za) ? za : ((camZ > zb) ? zb : camZ);
                        final double dx = camX - px, dz = camZ - pz;
                        if (dx * dx + dy2 + dz * dz > rangeSq) continue;

                        final int parity = (xi + faceCellYMin + zi) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(xa, minY, zb);
                        t.addVertex(xb, minY, zb);
                        t.addVertex(xb, minY, za);
                        t.addVertex(xa, minY, za);
                    }
                }
            }
        }

        // =========================
        // +X and -X faces (YZ grid)
        // =========================
        {
            // +X (x = maxX)
            final double dx = camX - maxX;
            final double dx2 = dx * dx;
            if (dx2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dx2);

                int yStart = clampY0.applyAsInt((int) Math.floor(camY - r));
                int yEnd   = clampY0.applyAsInt((int) Math.ceil (camY + r));
                int zStart = clampZ0.applyAsInt((int) Math.floor(camZ - r));
                int zEnd   = clampZ0.applyAsInt((int) Math.ceil (camZ + r));

                for (int yi = yStart; yi < yEnd; yi++) {
                    final double ya = Math.max(minY, yi);
                    final double yb = Math.min(maxY, yi + 1.0);
                    if (yb <= ya) continue;

                    for (int zi = zStart; zi < zEnd; zi++) {
                        final double za = Math.max(minZ, zi);
                        final double zb = Math.min(maxZ, zi + 1.0);
                        if (zb <= za) continue;

                        final double py = (camY < ya) ? ya : ((camY > yb) ? yb : camY);
                        final double pz = (camZ < za) ? za : ((camZ > zb) ? zb : camZ);
                        final double dy = camY - py, dz = camZ - pz;
                        if (dx2 + dy * dy + dz * dz > rangeSq) continue;

                        final int parity = (faceCellXMax + yi + zi) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(maxX, ya, za);
                        t.addVertex(maxX, ya, zb);
                        t.addVertex(maxX, yb, zb);
                        t.addVertex(maxX, yb, za);
                    }
                }
            }
        }

        {
            // -X (x = minX)
            final double dx = camX - minX;
            final double dx2 = dx * dx;
            if (dx2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dx2);

                int yStart = clampY0.applyAsInt((int) Math.floor(camY - r));
                int yEnd   = clampY0.applyAsInt((int) Math.ceil (camY + r));
                int zStart = clampZ0.applyAsInt((int) Math.floor(camZ - r));
                int zEnd   = clampZ0.applyAsInt((int) Math.ceil (camZ + r));

                for (int yi = yStart; yi < yEnd; yi++) {
                    final double ya = Math.max(minY, yi);
                    final double yb = Math.min(maxY, yi + 1.0);
                    if (yb <= ya) continue;

                    for (int zi = zStart; zi < zEnd; zi++) {
                        final double za = Math.max(minZ, zi);
                        final double zb = Math.min(maxZ, zi + 1.0);
                        if (zb <= za) continue;

                        final double py = (camY < ya) ? ya : ((camY > yb) ? yb : camY);
                        final double pz = (camZ < za) ? za : ((camZ > zb) ? zb : camZ);
                        final double dy = camY - py, dz = camZ - pz;
                        if (dx2 + dy * dy + dz * dz > rangeSq) continue;

                        final int parity = (faceCellXMin + yi + zi) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(minX, ya, zb);
                        t.addVertex(minX, ya, za);
                        t.addVertex(minX, yb, za);
                        t.addVertex(minX, yb, zb);
                    }
                }
            }
        }

        // =========================
        // +Z and -Z faces (XY grid)
        // =========================
        {
            // +Z (z = maxZ)
            final double dz = camZ - maxZ;
            final double dz2 = dz * dz;
            if (dz2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dz2);

                int xStart = clampX0.applyAsInt((int) Math.floor(camX - r));
                int xEnd   = clampX0.applyAsInt((int) Math.ceil (camX + r));
                int yStart = clampY0.applyAsInt((int) Math.floor(camY - r));
                int yEnd   = clampY0.applyAsInt((int) Math.ceil (camY + r));

                for (int xi = xStart; xi < xEnd; xi++) {
                    final double xa = Math.max(minX, xi);
                    final double xb = Math.min(maxX, xi + 1.0);
                    if (xb <= xa) continue;

                    for (int yi = yStart; yi < yEnd; yi++) {
                        final double ya = Math.max(minY, yi);
                        final double yb = Math.min(maxY, yi + 1.0);
                        if (yb <= ya) continue;

                        final double px = (camX < xa) ? xa : ((camX > xb) ? xb : camX);
                        final double py = (camY < ya) ? ya : ((camY > yb) ? yb : camY);
                        final double dx = camX - px, dy = camY - py;
                        if (dx * dx + dy * dy + dz2 > rangeSq) continue;

                        final int parity = (xi + yi + faceCellZMax) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(xa, ya, maxZ);
                        t.addVertex(xb, ya, maxZ);
                        t.addVertex(xb, yb, maxZ);
                        t.addVertex(xa, yb, maxZ);
                    }
                }
            }
        }

        {
            // -Z (z = minZ)
            final double dz = camZ - minZ;
            final double dz2 = dz * dz;
            if (dz2 <= rangeSq) {
                final double r = Math.sqrt(rangeSq - dz2);

                int xStart = clampX0.applyAsInt((int) Math.floor(camX - r));
                int xEnd   = clampX0.applyAsInt((int) Math.ceil (camX + r));
                int yStart = clampY0.applyAsInt((int) Math.floor(camY - r));
                int yEnd   = clampY0.applyAsInt((int) Math.ceil (camY + r));

                for (int xi = xStart; xi < xEnd; xi++) {
                    final double xa = Math.max(minX, xi);
                    final double xb = Math.min(maxX, xi + 1.0);
                    if (xb <= xa) continue;

                    for (int yi = yStart; yi < yEnd; yi++) {
                        final double ya = Math.max(minY, yi);
                        final double yb = Math.min(maxY, yi + 1.0);
                        if (yb <= ya) continue;

                        final double px = (camX < xa) ? xa : ((camX > xb) ? xb : camX);
                        final double py = (camY < ya) ? ya : ((camY > yb) ? yb : camY);
                        final double dx = camX - px, dy = camY - py;
                        if (dx * dx + dy * dy + dz2 > rangeSq) continue;

                        final int parity = (xi + yi + faceCellZMin) & 1;
                        if (parity == 0) t.setColorRGBA(aR, aG, aB, aA);
                        else             t.setColorRGBA(bR, bG, bB, bA);

                        t.addVertex(xb, ya, minZ);
                        t.addVertex(xa, ya, minZ);
                        t.addVertex(xa, yb, minZ);
                        t.addVertex(xb, yb, minZ);
                    }
                }
            }
        }

        t.draw();

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    public static void correctForCamera(RenderWorldLastEvent e) {
        final float pt = e.partialTicks;
        final Minecraft mc = Minecraft.getMinecraft();
        final Entity view = mc.renderViewEntity;

        final double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        final double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        final double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        GL11.glTranslated(-camX, -camY, -camZ);
    }

}
