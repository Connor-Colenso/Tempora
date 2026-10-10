package com.colen.tempora.loggers.entity_position;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

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

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_POSITION;
    }

    @Override
    public @NotNull String getShortName() {
        return "entity";
    }

    @Override
    public @NotNull EntityPositionEventInfo newEventInfo() {
        return new EntityPositionEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (EntityPositionEventInfo epEventInfo : transparentEventsToRenderInWorld) {
            Entity entity = EntityList.createEntityByName(epEventInfo.entityName, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(
                entity,
                epEventInfo.x,
                epEventInfo.y,
                epEventInfo.z,
                epEventInfo.rotationYaw,
                epEventInfo.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, epEventInfo.x, epEventInfo.y, epEventInfo.z, 0, 1.0, 0);
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
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entityLiving.ticksExisted % entityMovementLoggingInterval != 0) return; // As an example, track every
                                                                                          // 20 seconds.

        logEntityPosition(event.entityLiving);
    }

    /**
     * Logs where an entity is right now. Players are not logged here (see PlayerMovementLogger), nor are dropped items
     * and XP orbs.
     */
    public void logEntityPosition(Entity entity) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;
        if (entity instanceof EntityItem) return;
        if (entity instanceof EntityXPOrb) return;

        EntityPositionEventInfo eventInfo = new EntityPositionEventInfo();
        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        eventInfo.entityName = entity.getCommandSenderName();
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}
