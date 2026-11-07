package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkPosition;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.EventRenderer;
import com.colen.tempora.utils.ChunkPositionUtils;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Each explosion as a TNT block at its centre with a ray to every block it destroyed
@SideOnly(Side.CLIENT)
public class ExplosionRenderer implements EventRenderer<ExplosionEventInfo> {

    private static final Color COLOUR = Color.MAGENTA;

    // Decoded once per event not every frame, a big explosion can destroy thousands of blocks. Weak so each goes once
    // ClientEventStore drops its event
    private final Map<ExplosionEventInfo, Set<ChunkPosition>> destroyedBlocks = new WeakHashMap<>();

    @Override
    public void clearCaches() {
        destroyedBlocks.clear();
    }

    @Override
    public void renderWorld(List<ExplosionEventInfo> events, EventRenderContext context) {
        for (ExplosionEventInfo event : events) {
            // Centre isn't on the block grid so centre the TNT on it
            RenderUtils.renderBlockInWorld(
                event.x - 0.5,
                event.y - 0.5,
                event.z - 0.5,
                Blocks.tnt,
                0,
                null,
                COLOUR,
                context.partialTicks);

            renderRays(
                event,
                destroyedBlocks.computeIfAbsent(
                    event,
                    explosion -> ChunkPositionUtils.decodePositions(explosion.affectedBlockCoordinates)));
        }
    }

    // All of one explosion's rays in one draw
    private static void renderRays(ExplosionEventInfo event, Set<ChunkPosition> destroyed) {
        if (destroyed.isEmpty()) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1F);

            Tessellator tes = Tessellator.instance;
            tes.startDrawing(GL11.GL_LINES);
            tes.setColorRGBA(COLOUR.getRed(), COLOUR.getGreen(), COLOUR.getBlue(), 160);
            for (ChunkPosition block : destroyed) {
                tes.addVertex(event.x, event.y, event.z);
                tes.addVertex(block.chunkPosX + 0.5, block.chunkPosY + 0.5, block.chunkPosZ + 0.5);
            }
            tes.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void renderText(List<ExplosionEventInfo> events, EventRenderContext context) {
        for (ExplosionEventInfo event : events) {
            List<String> lines = Arrays.asList(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted(
                    "tempora.render.event.explosion",
                    NumberFormatUtil.formatNumber(event.power, ONE_DP)),
                EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));

            EventRenderHelpers.renderLabelAbove(lines, event.x, event.y + 0.7, event.z);
        }
    }
}
