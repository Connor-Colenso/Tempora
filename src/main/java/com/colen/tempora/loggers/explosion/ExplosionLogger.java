package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.colen.tempora.enums.LoggerEventType;
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

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.ChunkPositionUtils;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.ExplosionLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {

        List<ExplosionQueueElement> sortedList = getSortedLatestEventsByDistance(transparentEventsToRenderInWorld, e);
        Tessellator tessellator = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;

        // compute player interpolated position once
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks;

        for (ExplosionQueueElement exqe : sortedList) {
            // Draw explosion center as TNT block
            RenderUtils.renderBlockInWorld(
                e,
                exqe.x - 0.5,
                exqe.y - 0.5,
                exqe.z - 0.5,
                Block.getIdFromBlock(Blocks.tnt),
                0,
                null,
                getLoggerType());

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
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            ExplosionQueueElement queueElement = new ExplosionQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            String exploderUUID = resultSet.getString("exploderUUID");
            if (exploderUUID.equals(TemporaUtils.UNKNOWN_PLAYER_NAME)) {
                queueElement.exploderUUID = exploderUUID;
            } else {
                queueElement.exploderUUID = PlayerUtils.UUIDToName(resultSet.getString("exploderUUID"));
            }

            String closestPlayerUUID = resultSet.getString("closestPlayerUUID");
            if (closestPlayerUUID.equals(TemporaUtils.UNKNOWN_PLAYER_NAME)) {
                queueElement.closestPlayerUUID = closestPlayerUUID;
            } else {
                queueElement.closestPlayerUUID = PlayerUtils.UUIDToName(closestPlayerUUID);
            }

            queueElement.strength = resultSet.getFloat("strength");
            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");
            queueElement.affectedBlockCoordinates = resultSet.getString("affectedBlockCoordinates");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("strength", "REAL", "NOT NULL DEFAULT -1"),
            new ColumnDef("exploderUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"),
            new ColumnDef("affectedBlockCoordinates", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA));
    }

    @Override
    public void threadedSaveEvents(List<ExplosionQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (strength, exploderUUID, closestPlayerUUID, closestPlayerDistance, affectedBlockCoordinates, eventID, x, y, z, dimensionID, timestamp, versionID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (ExplosionQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setFloat(index++, queueElement.strength);
                pstmt.setString(index++, queueElement.exploderUUID);
                pstmt.setString(index++, queueElement.closestPlayerUUID);
                pstmt.setDouble(index++, queueElement.closestPlayerDistance);
                pstmt.setString(index++, queueElement.affectedBlockCoordinates);
                DatabaseUtils.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
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
        queueElement.dimensionId = world.provider.dimensionId;
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
