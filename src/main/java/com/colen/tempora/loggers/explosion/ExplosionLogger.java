package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.ChunkPositionUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionEventInfo> {

    @Override
    public @NotNull ExplosionEventInfo newEventInfo() {
        return new ExplosionEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {

        List<ExplosionEventInfo> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);
        Tessellator tessellator = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;

        // compute player interpolated position once
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * renderEvent.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * renderEvent.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * renderEvent.partialTicks;

        for (ExplosionEventInfo exp_ev_info : sortedList) {
            // Draw explosion centre as a TNT block
            RenderUtils.renderBlockInWorld(
                renderEvent,
                exp_ev_info.x - 0.5,
                exp_ev_info.y - 0.5,
                exp_ev_info.z - 0.5,
                Block.getIdFromBlock(Blocks.tnt),
                0,
                null,
                this);

            // Draw purple lines to affected blocks
            for (ChunkPosition chunkPosition : ChunkPositionUtils
                .decodePositions(exp_ev_info.affectedBlockCoordinates)) {
                double startX = exp_ev_info.x;
                double startY = exp_ev_info.y;
                double startZ = exp_ev_info.z;

                double endX = chunkPosition.chunkPosX + 0.5;
                double endY = chunkPosition.chunkPosY + 0.5;
                double endZ = chunkPosition.chunkPosZ + 0.5;

                // single push for both state changes and translation
                GL11.glPushMatrix();
                try {
                    // Translate to camera-relative world coords
                    GL11.glTranslated(-px, -py, -pz);

                    // GL state for drawing unlit, untextured lines
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glLineWidth(0.5f);

                    // Draw line in world coords (now camera-relative)
                    tessellator.startDrawing(GL11.GL_LINES);
                    tessellator.setColorRGBA(255, 0, 255, 255);
                    tessellator.addVertex(startX, startY, startZ);
                    tessellator.addVertex(endX, endY, endZ);
                    tessellator.draw();

                    // Restore GL state we changed
                    GL11.glLineWidth(1.0F);
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glEnable(GL11.GL_CULL_FACE);
                    GL11.glEnable(GL11.GL_LIGHTING);
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                } finally {
                    GL11.glPopMatrix();
                }
            }
        }
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }

    @Override
    public @NotNull String getShortName() {
        return "explosion";
    }

    @Override
    public Color getColour() {
        return Color.MAGENTA;
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(final @NotNull ExplosionEvent.Detonate event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        Explosion explosion = event.explosion;
        // Whoever lit the TNT, or the creeper itself, rather than the TNT entity.
        Entity exploder = explosion.getExplosivePlacedBy() != null ? explosion.getExplosivePlacedBy()
            : explosion.exploder;

        logExplosion(
            event.world,
            explosion.explosionX,
            explosion.explosionY,
            explosion.explosionZ,
            explosion.explosionSize,
            exploder,
            explosion.affectedBlockPositions);
    }

    /**
     * Logs an explosion. {@code exploder} is whatever is responsible, if known: a living entity is stored by UUID, any
     * other entity by its class name. {@code affectedBlocks} are the positions it destroyed, if known.
     */
    public void logExplosion(World world, double x, double y, double z, float strength, @Nullable Entity exploder,
        @Nullable List<ChunkPosition> affectedBlocks) {
        if (!isEnabledByConfig) return;

        ExplosionEventInfo eventInfo = new ExplosionEventInfo();
        eventInfo.strength = strength;

        if (exploder instanceof EntityLivingBase) {
            eventInfo.exploderUUID = exploder.getUniqueID()
                .toString();
        } else if (exploder != null) {
            eventInfo.exploderUUID = exploder.getClass()
                .getSimpleName();
        } else {
            eventInfo.exploderUUID = UNKNOWN_CAUSE;
        }

        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        eventInfo.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        eventInfo.closestPlayerDistance = closestPlayer != null ? closestPlayer.getDistance(x, y, z) : -1;

        eventInfo.affectedBlockCoordinates = ChunkPositionUtils
            .encodePositions(affectedBlocks == null ? Collections.emptyList() : affectedBlocks);

        logEvent(eventInfo, world.provider.dimensionId, x, y, z);
    }

}
