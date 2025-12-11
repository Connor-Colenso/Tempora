package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.PlayerUtils.isUUID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.EventLoggingHelper;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntityDeathLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, e);

        for (EntityDeathQueueElement bcqe : transparentEventsToRenderInWorld) {
            Entity entity = EntityList.createEntityByName(bcqe.nameOfDeadMob, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(entity, bcqe.x, bcqe.y, bcqe.z, bcqe.rotationYaw, bcqe.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, bcqe.x, bcqe.y, bcqe.z, 1.0, 0, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (isClientSide()) return;
        if (event.entityLiving instanceof EntityPlayerMP) return; // No players allowed here, this is for mobs only.
        if (event.entity instanceof EntityItem) return;
        if (event.entity instanceof EntityXPOrb) return;
        if (event.isCanceled()) return;

        EntityDeathQueueElement queueElement = new EntityDeathQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.entity.posX;
        queueElement.y = event.entity.posY;
        queueElement.z = event.entity.posZ;
        queueElement.dimensionId = event.entity.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.nameOfDeadMob = EntityList.getEntityString(event.entityLiving);
        queueElement.entityUUID = event.entityLiving.getUniqueID()
            .toString();

        queueElement.rotationYaw = event.entityLiving.rotationYaw;
        queueElement.rotationPitch = event.entityLiving.rotationPitch;

        // Get what killed it.
        Entity trueSource = event.source.getEntity();
        if (trueSource != null) {
            if (trueSource instanceof EntityPlayerMP player) {
                // This is specific for players
                queueElement.killedBy = player.getUniqueID()
                    .toString();
            } else {
                // For non-player entities
                queueElement.killedBy = "[" + trueSource.getClass()
                    .getSimpleName() + "]";
            }
        } else {
            queueElement.killedBy = "[" + event.source.damageType + "]";
        }

        queueEvent(queueElement);
    }

    @Override
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {
            EntityDeathQueueElement queueElement = new EntityDeathQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            String killedBy = resultSet.getString("killedBy");
            if (isUUID(killedBy)) {
                queueElement.killedBy = PlayerUtils.UUIDToName(killedBy);
            } else {
                queueElement.killedBy = killedBy;
            }

            queueElement.nameOfDeadMob = resultSet.getString("entityName");
            queueElement.entityUUID = resultSet.getString("entityUUID");
            queueElement.rotationYaw = resultSet.getFloat("rotationYaw");
            queueElement.rotationPitch = resultSet.getFloat("rotationPitch");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("entityName", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("entityUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("killedBy", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("rotationYaw", "REAL", "NOT NULL DEFAULT 0"),
            new ColumnDef("rotationPitch", "REAL", "NOT NULL DEFAULT 0"));
    }

    @Override
    public void threadedSaveEvents(List<EntityDeathQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (entityName, entityUUID, killedBy, rotationYaw, rotationPitch, eventID, x, y, z, dimensionID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (EntityDeathQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setString(index++, queueElement.nameOfDeadMob);
                pstmt.setString(index++, queueElement.entityUUID);
                pstmt.setString(index++, queueElement.killedBy);
                pstmt.setFloat(index++, queueElement.rotationYaw);
                pstmt.setFloat(index++, queueElement.rotationPitch);

                EventLoggingHelper.defaultColumnEntries(queueElement, pstmt, index);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

}
