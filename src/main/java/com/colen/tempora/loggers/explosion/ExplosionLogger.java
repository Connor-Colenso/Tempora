package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.EventLoggingHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.ExplosionLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {

        List<GenericQueueElement> sortedList = RenderUtils.getSortedLatestEventsByDistance(eventsToRenderInWorld, e);

        for (GenericQueueElement element : sortedList) {
            RenderUtils.renderBlockInWorld(e, element.x - 0.5, element.y - 0.5, element.z - 0.5, Block.getIdFromBlock(Blocks.tnt), 0, null, getLoggerType());
        }
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {

            ExplosionQueueElement queueElement = new ExplosionQueueElement();
            queueElement.x = resultSet.getDouble("x");
            queueElement.y = resultSet.getDouble("y");
            queueElement.z = resultSet.getDouble("z");
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.strength = resultSet.getFloat("strength");

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

            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");
            queueElement.timestamp = resultSet.getLong("timestamp");

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
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void threadedSaveEvents(List<ExplosionQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (strength, exploderUUID, closestPlayerUUID, closestPlayerDistance, eventID, x, y, z, dimensionID, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (ExplosionQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setFloat(index++, queueElement.strength);
                pstmt.setString(index++, queueElement.exploderUUID);
                pstmt.setString(index++, queueElement.closestPlayerUUID);
                pstmt.setDouble(index++, queueElement.closestPlayerDistance);
                EventLoggingHelper.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
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
        final String exploderName = (exploder != null) ? exploder.getUniqueID()
            .toString() : TemporaUtils.UNKNOWN_PLAYER_NAME;

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
        queueElement.x = event.explosion.explosionX;
        queueElement.y = event.explosion.explosionY;
        queueElement.z = event.explosion.explosionZ;
        queueElement.dimensionId = world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.strength = strength;
        queueElement.exploderUUID = exploderName;
        queueElement.closestPlayerUUID = closestPlayerName;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

}
