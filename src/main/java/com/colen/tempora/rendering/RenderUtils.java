package com.colen.tempora.rendering;

import java.awt.Color;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.colen.tempora.mixins.accessors.ForgeHooksClientAccessor;
import com.colen.tempora.rendering.FakeWorld.FakeWorld;

import appeng.block.networking.BlockCableBus;
import appeng.client.render.BusRenderHelper;
import cpw.mods.fml.common.Loader;

public abstract class RenderUtils {

    private static final boolean AE2_LOADED = Loader.isModLoaded("appliedenergistics2");

    // Full bright lightmap coords so stuff shows up at night and underground
    private static final float FULL_BRIGHT = 240f;

    // Blocks drawn a bit smaller than full so one still shows inside a block that's there now
    private static final double GHOST_BLOCK_SCALE = 14.0 / 16.0;

    // Draws an entity at world coords (camera offset already applied) facing the given way, full bright. Also moves it
    // there so its bounding box can be outlined after with renderEntityAABBInWorld
    public static void renderEntityInWorld(Entity entity, double x, double y, double z, float rotationYaw,
        float rotationPitch, float partialTicks) {
        entity.setPosition(x, y, z);
        entity.lastTickPosX = entity.prevPosX = x;
        entity.lastTickPosY = entity.prevPosY = y;
        entity.lastTickPosZ = entity.prevPosZ = z;

        entity.prevRotationYaw = entity.rotationYaw = rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch = rotationPitch;

        if (entity instanceof EntityLivingBase living) {
            living.renderYawOffset = living.prevRenderYawOffset = rotationYaw;
            living.rotationYawHead = living.prevRotationYawHead = rotationYaw;
        }

        // Capes trail towards where the player was last tick, and a player that never ticks never updates that, so it'd
        // point back at wherever it was made. Current position makes the cape hang straight down
        if (entity instanceof EntityPlayer player) {
            player.field_71091_bM = player.field_71094_bP = x;
            player.field_71096_bN = player.field_71095_bQ = y;
            player.field_71097_bO = player.field_71085_bR = z;
        }

        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glColor4f(1, 1, 1, 1);
            RenderHelper.enableStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);

            RenderManager.instance.renderEntityWithPosYaw(entity, 0.0D, 0.0D, 0.0D, rotationYaw, partialTicks);
        } finally {
            // Also when the entity's renderer threw, so the rest of the frame isn't drawn with its state
            RenderHelper.disableStandardItemLighting();
            GL11.glColor4f(1, 1, 1, 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);

            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    // Outlines an entity's bounding box where renderEntityInWorld last put it, through walls
    public static void renderEntityAABBInWorld(Entity entity, Color colour) {
        AxisAlignedBB aabb = entity.boundingBox;
        renderRegion(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ, colour);
    }

    // Draws a block at a block position in world coords (camera offset already applied). A highlight colour outlines it
    // too, blinking
    public static void renderBlockInWorld(double x, double y, double z, Block block, int metadata,
        @Nullable TileEntity tileEntity, @Nullable Color highlight, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        float prevBrightnessX = OpenGlHelper.lastBrightnessX;
        float prevBrightnessY = OpenGlHelper.lastBrightnessY;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            // RenderBlocks bakes shading and tint (grass colour etc) into vertex colours. GL lighting left on by
            // something earlier this frame ignores those and draws the block dark grey
            GL11.glDisable(GL11.GL_LIGHTING);

            GL11.glColor4f(1, 1, 1, 1);
            mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);

            RenderBlocks rb = new RenderBlocks();
            rb.useInventoryTint = false;

            // Block is rendered at the origin so the fake world answers for the origin
            FakeWorld fakeWorld = new FakeWorld();
            fakeWorld.block = block;
            fakeWorld.tileEntity = tileEntity;
            fakeWorld.metadata = metadata;
            rb.blockAccess = fakeWorld;

            if (AE2_LOADED) prepareAE2CableBus(block);

            GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
            GL11.glScaled(GHOST_BLOCK_SCALE, GHOST_BLOCK_SCALE, GHOST_BLOCK_SCALE);
            GL11.glTranslated(-0.5, -0.5, -0.5);

            renderBlockPasses(rb, block);

            // Some blocks like chests are drawn by their TE not RenderBlocks
            if (tileEntity != null) {
                TileEntitySpecialRenderer special = TileEntityRendererDispatcher.instance
                    .getSpecialRenderer(tileEntity);
                if (special != null) {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
                    special.renderTileEntityAt(tileEntity, 0, 0, 0, partialTicks);
                }
            }

            if (highlight != null && System.currentTimeMillis() / 500 % 2 == 0) {
                renderRegion(0, 0, 0, 1, 1, 1, highlight);
            }
        } finally {
            GL11.glPopMatrix();

            // Put back what vanilla/Forge rendering expects
            GL11.glColor4f(1, 1, 1, 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); // Block textures
            mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);
            GL11.glEnable(GL11.GL_LIGHTING); // Vanilla expects this on

            GL11.glPopAttrib();
        }
    }

    // Outside chunk rendering Forge's render pass is -1, and some renderers (GT machines) only draw their textures in
    // the pass they belong to, so walk the passes like chunk rendering does
    private static void renderBlockPasses(RenderBlocks rb, Block block) {
        Tessellator tes = Tessellator.instance;
        int prevRenderPass = ForgeHooksClient.getWorldRenderPass();
        try {
            for (int pass = 0; pass < 2; pass++) {
                if (!block.canRenderInPass(pass)) continue;
                ForgeHooksClientAccessor.tempora$setWorldRenderPass(pass);
                if (pass == 1) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                }
                tes.startDrawingQuads();
                try {
                    rb.renderBlockByRenderType(block, 0, 0, 0);
                } finally {
                    // Even if the block's renderer threw, otherwise all later drawing fails with "Already tesselating!"
                    tes.draw();
                }
            }
        } finally {
            ForgeHooksClientAccessor.tempora$setWorldRenderPass(prevRenderPass);
        }
    }

    // AE2 cable bus draws nothing unless told which pass it's in
    private static void prepareAE2CableBus(Block block) {
        if (block instanceof BlockCableBus) {
            BusRenderHelper.instances.get()
                .setPass(0);
        }
    }

    public static void renderRegion(double startX, double startY, double startZ, double endX, double endY, double endZ,
        Color colour) {
        renderRegion(
            startX,
            startY,
            startZ,
            endX,
            endY,
            endZ,
            colour.getRed() / 255.0,
            colour.getGreen() / 255.0,
            colour.getBlue() / 255.0);
    }

    public static void renderRegion(double startX, double startY, double startZ, double endX, double endY, double endZ,
        double red, double green, double blue) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT); // Lighting, texture,
                                                                                             // blend, depth etc
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
        GL11.glPopAttrib();
    }

    public static void renderFloatingText(List<String> textLines, double x, double y, double z) {
        renderFloatingText(textLines, x, y, z, 0xFFFFFF);
    }

    // Lines of text centred on a position, facing the camera, through walls
    public static void renderFloatingText(List<String> textLines, double x, double y, double z, int color) {
        RenderManager renderManager = RenderManager.instance;
        FontRenderer fontrenderer = Minecraft.getMinecraft().fontRenderer;
        if (fontrenderer == null || textLines.isEmpty()) return;

        // Restores lighting, depth and blending to how they were instead of forcing them on or off after. Leaving
        // lighting on made blocks drawn after the text come out dark grey
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-0.025F, -0.025F, 0.025F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int fontHeight = fontrenderer.FONT_HEIGHT;
        // Start so the whole block of text is vertically centred
        int totalHeight = textLines.size() * fontHeight;
        int yOffset = -totalHeight / 2;

        int halfWidth = 0;
        for (String line : textLines) {
            halfWidth = Math.max(halfWidth, fontrenderer.getStringWidth(line) / 2);
        }

        // Dark backing like a name tag so text reads against bright blocks and sky
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.35F);
        tes.addVertex(-halfWidth - 2, yOffset - 1, 0.0D);
        tes.addVertex(-halfWidth - 2, yOffset + totalHeight, 0.0D);
        tes.addVertex(halfWidth + 2, yOffset + totalHeight, 0.0D);
        tes.addVertex(halfWidth + 2, yOffset - 1, 0.0D);
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (String line : textLines) {
            fontrenderer.drawString(line, -fontrenderer.getStringWidth(line) / 2, yOffset, color);
            yOffset += fontHeight;
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();
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

    // Called from RenderWorldLastEvent with the camera translation already applied
    public static void renderBoundingBox(double startX, double startY, double startZ, double endX, double endY,
        double endZ) {

        // Allow start > end, shrink a bit to stop z-fighting
        final double minX = Math.min(startX, endX);
        final double minY = Math.min(startY, endY);
        final double minZ = Math.min(startZ, endZ);
        final double maxX = Math.max(startX, endX);
        final double maxY = Math.max(startY, endY);
        final double maxZ = Math.max(startZ, endZ);

        // Light grey
        final float r = 0.75f;
        final float g = 0.75f;
        final float b = 0.75f;
        final float a = 0.25f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Keep depth test so it clips against terrain but don't write depth (usual for translucent)
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, a);

        // Bottom (Y=min)
        t.addVertex(minX, minY, minZ);
        t.addVertex(maxX, minY, minZ);
        t.addVertex(maxX, minY, maxZ);
        t.addVertex(minX, minY, maxZ);

        // Top (Y=max)
        t.addVertex(minX, maxY, minZ);
        t.addVertex(minX, maxY, maxZ);
        t.addVertex(maxX, maxY, maxZ);
        t.addVertex(maxX, maxY, minZ);

        // North (Z=min)
        t.addVertex(minX, minY, minZ);
        t.addVertex(minX, maxY, minZ);
        t.addVertex(maxX, maxY, minZ);
        t.addVertex(maxX, minY, minZ);

        // South (Z=max)
        t.addVertex(minX, minY, maxZ);
        t.addVertex(maxX, minY, maxZ);
        t.addVertex(maxX, maxY, maxZ);
        t.addVertex(minX, maxY, maxZ);

        // West (X=min)
        t.addVertex(minX, minY, minZ);
        t.addVertex(minX, minY, maxZ);
        t.addVertex(minX, maxY, maxZ);
        t.addVertex(minX, maxY, minZ);

        // East (X=max)
        t.addVertex(maxX, minY, minZ);
        t.addVertex(maxX, maxY, minZ);
        t.addVertex(maxX, maxY, maxZ);
        t.addVertex(maxX, minY, maxZ);

        t.draw();

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }

}
