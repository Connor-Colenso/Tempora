package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerBlockBreakLogger;
    }

    @Override
    public void renderEventInWorld(RenderWorldLastEvent e) {
        for (GenericQueueElement element : eventsToRenderInWorld) {
            if (element instanceof PlayerBlockBreakQueueElement playerBlockBreakQueueElement) {

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

                if (playerBlockBreakQueueElement.dimensionId != curDim) continue;

                GL11.glPushMatrix();
                GL11.glTranslated(
                    playerBlockBreakQueueElement.x + 0.5,
                    playerBlockBreakQueueElement.y + 0.5,
                    playerBlockBreakQueueElement.z + 0.5);
                double SCALE_FACTOR = 0.8;
                GL11.glScaled(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);

                tes.startDrawingQuads();
                RenderUtils.addRenderedBlockInWorld(
                    Block.getBlockById(playerBlockBreakQueueElement.blockID),
                    playerBlockBreakQueueElement.metadata,
                    0,
                    0,
                    0);
                tes.draw();

                GL11.glPopMatrix();

                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glPopMatrix();
            }
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("metadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("blockId", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("pickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"));
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {

        try {
            ArrayList<GenericQueueElement> eventList = new ArrayList<>();

            while (resultSet.next()) {

                PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
                queueElement.x = resultSet.getInt("x");
                queueElement.y = resultSet.getInt("y");
                queueElement.z = resultSet.getInt("z");
                queueElement.dimensionId = resultSet.getInt("dimensionID");
                queueElement.timestamp = resultSet.getLong("timestamp");

                queueElement.playerUUIDWhoBrokeBlock = resultSet.getString("playerUUID");
                queueElement.blockID = resultSet.getInt("blockId");
                queueElement.metadata = resultSet.getInt("metadata");
                queueElement.pickBlockID = resultSet.getInt("pickBlockID");
                queueElement.pickBlockMeta = resultSet.getInt("pickBlockMeta");

                eventList.add(queueElement);
            }

            return eventList;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void threadedSaveEvents(List<PlayerBlockBreakQueueElement> elements) throws SQLException {
        if (elements == null || elements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, blockId, metadata, pickBlockID, pickBlockMeta, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (PlayerBlockBreakQueueElement elem : elements) {
                pstmt.setString(1, elem.playerUUIDWhoBrokeBlock);
                pstmt.setInt(2, elem.blockID);
                pstmt.setInt(3, elem.metadata);
                pstmt.setInt(4, elem.pickBlockID);
                pstmt.setInt(5, elem.pickBlockMeta);
                pstmt.setDouble(6, elem.x);
                pstmt.setDouble(7, elem.y);
                pstmt.setDouble(8, elem.z);
                pstmt.setInt(9, elem.dimensionId);
                pstmt.setTimestamp(10, new Timestamp(elem.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionId = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = getPickBlockSafe(event.block, event.world, event.x, event.y, event.z);
        if (pickStack != null && pickStack.getItem() != null) {
            queueElement.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            queueElement.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            queueElement.pickBlockID = queueElement.blockID;
            queueElement.pickBlockMeta = queueElement.metadata;
        }

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerUUIDWhoBrokeBlock = event.getPlayer()
                .getUniqueID()
                .toString();
        } else {
            queueElement.playerUUIDWhoBrokeBlock = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }
}
