package com.colen.tempora.loggers.entity_position;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.colen.tempora.loggers.block_change.BlockChangeQueueElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.DatabaseUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionQueueElement> {

    @Override
    public @NotNull EntityPositionQueueElement getQueueElementInstance() {
        return new EntityPositionQueueElement();
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.EntityPositionLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, e);

        for (EntityPositionQueueElement epqh : transparentEventsToRenderInWorld) {
            Entity entity = EntityList.createEntityByName(epqh.entityName, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(entity, epqh.x, epqh.y, epqh.z, epqh.rotationYaw, epqh.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, epqh.x, epqh.y, epqh.z, 0, 1.0, 0);
        }
    }

    private static int entityMovementLoggingInterval;

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        entityMovementLoggingInterval = config.getInt(
            "entity_position_logging_interval",
            getLoggerName(),
            500,
            1,
            Integer.MAX_VALUE,
            "How frequently are non-player entity locations recorded to the database, measured in ticks (20 ticks per second)?");
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entityLiving.ticksExisted % entityMovementLoggingInterval != 0) return; // As an example, track every
                                                                                          // 20 seconds.
        if (event.entityLiving instanceof EntityPlayerMP) return; // Do not track players here, we do this elsewhere.
        if (event.entity instanceof EntityItem) return;
        if (event.entity instanceof EntityXPOrb) return;

        EntityPositionQueueElement queueElement = new EntityPositionQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.entityLiving.posX;
        queueElement.y = event.entityLiving.posY;
        queueElement.z = event.entityLiving.posZ;
        queueElement.dimensionID = event.entityLiving.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.rotationYaw = event.entityLiving.rotationYaw;
        queueElement.rotationPitch = event.entityLiving.rotationPitch;

        queueElement.entityName = event.entityLiving.getCommandSenderName();
        queueElement.entityUUID = event.entityLiving.getUniqueID()
            .toString();

        queueEvent(queueElement);
    }

}
