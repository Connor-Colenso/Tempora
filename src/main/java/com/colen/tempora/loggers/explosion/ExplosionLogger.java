package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.awt.Color;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.ChunkPositionUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public @NotNull ExplosionQueueElement getQueueElementInstance() {
        return new ExplosionQueueElement();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {

        List<ExplosionQueueElement> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);
        Tessellator tessellator = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;

        // compute player interpolated position once
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * renderEvent.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * renderEvent.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * renderEvent.partialTicks;

        for (ExplosionQueueElement exqe : sortedList) {
            // Draw explosion center as TNT block
            RenderUtils.renderBlockInWorld(
                renderEvent,
                exqe.x - 0.5,
                exqe.y - 0.5,
                exqe.z - 0.5,
                Block.getIdFromBlock(Blocks.tnt),
                0,
                null,
                this);

            // Draw purple lines to affected blocks
            for (ChunkPosition chunkPosition : ChunkPositionUtils.decodePositions(exqe.affectedBlockCoordinates)) {
                double startX = exqe.x;
                double startY = exqe.y;
                double startZ = exqe.z;

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
    public String getLoggerName() {
        return TemporaEvents.EXPLOSION;
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
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        final World world = event.world;
        final float strength = event.explosion.explosionSize;
        final double x = event.explosion.explosionX;
        final double y = event.explosion.explosionY;
        final double z = event.explosion.explosionZ;
        final Entity exploder = event.explosion.getExplosivePlacedBy();
        final String exploderName;
        if (exploder != null) {
            exploderName = exploder.getUniqueID()
                .toString();
        } else if (event.explosion.exploder != null) {
            exploderName = event.explosion.exploder.getClass()
                .getSimpleName();
        } else {
            exploderName = "[UNKNOWN]";
        }

        // Todo use built ins to clean this up further.
        EntityPlayer closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (EntityPlayer player : world.playerEntities) {
            double distance = player.getDistanceSq(x, y, z);
            if (distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }

        String closestPlayerName = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : TemporaUtils.UNKNOWN_PLAYER_NAME;
        closestDistance = Math.sqrt(closestDistance); // Convert from square distance to actual distance

        ExplosionQueueElement queueElement = new ExplosionQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.explosion.explosionX;
        queueElement.y = event.explosion.explosionY;
        queueElement.z = event.explosion.explosionZ;
        queueElement.dimensionID = world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.strength = strength;
        queueElement.exploderUUID = exploderName;
        queueElement.closestPlayerUUID = closestPlayerName;
        queueElement.closestPlayerDistance = closestDistance;

        queueElement.affectedBlockCoordinates = ChunkPositionUtils
            .encodePositions(event.explosion.affectedBlockPositions);

        queueEvent(queueElement);
    }

}
