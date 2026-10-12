package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.GenericUtils.isClientSide;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.mixin_interfaces.IEntityMixin;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_SPAWN;
    }

    @Override
    public @NotNull String getShortName() {
        return "spawn";
    }

    @Override
    public @NotNull EntitySpawnEventInfo newEventInfo() {
        return new EntitySpawnEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        logEntitySpawn(event.entity);
    }

    /**
     * Logs an entity appearing in the world. Each entity is only ever logged once, so calling this for one that was
     * already logged does nothing. Players, dropped items and XP orbs are not logged.
     */
    public void logEntitySpawn(Entity entity) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;
        if (entity instanceof EntityItem) return;
        if (entity instanceof EntityXPOrb) return;

        IEntityMixin entityMixin = (IEntityMixin) entity;
        if (entityMixin.getTempora$HasBeenLogged()) return;

        // Mark as logged, this is persistent with nbt.
        entityMixin.setTempora$HasBeenLogged(true);

        EntitySpawnEventInfo eventInfo = new EntitySpawnEventInfo();
        eventInfo.entityName = entityRegistryName(entity);
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}
