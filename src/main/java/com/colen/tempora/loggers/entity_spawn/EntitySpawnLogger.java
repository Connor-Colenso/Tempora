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
import com.colen.tempora.utils.WorldGenPhaseTracker;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_SPAWN;
    }

    @Override
    public @NotNull String getShortName() {
        return "mobspawn";
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

    // Logs an entity appearing in the world. Each entity is only ever logged once, so calling this again does nothing.
    // Not players, dropped items or XP orbs
    public void logEntitySpawn(Entity entity) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;
        if (entity instanceof EntityItem) return;
        if (entity instanceof EntityXPOrb) return;

        IEntityMixin entityMixin = (IEntityMixin) entity;
        if (entityMixin.getTempora$HasBeenLogged()) return;

        // Mark as logged, saved to NBT
        entityMixin.setTempora$HasBeenLogged(true);

        // Placed by world gen, e.g. a new chunk's animals. Still marked so it doesn't get logged when the chunk loads
        // again
        if (WorldGenPhaseTracker.isWorldGen()) return;

        EntitySpawnEventInfo eventInfo = new EntitySpawnEventInfo();
        eventInfo.entityName = entityRegistryName(entity);
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}
