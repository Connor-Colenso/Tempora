package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.GenericUtils.isClientSide;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathEventInfo> {

    // The player who killed it, if one did.
    @Override
    protected String playerColumn() {
        return "killerPlayer";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_DEATH;
    }

    @Override
    public @NotNull String getShortName() {
        return "mobdeath";
    }

    @Override
    public @NotNull EntityDeathEventInfo newEventInfo() {
        return new EntityDeathEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityDeath(LivingDeathEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        logEntityDeath(event.entityLiving, event.source);
    }

    /** Logs a mob dying, and what killed it. Players are not logged here, they have the PlayerDeathLogger. */
    public void logEntityDeath(EntityLivingBase entity, @Nullable DamageSource source) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;

        EntityDeathEventInfo eventInfo = new EntityDeathEventInfo();
        eventInfo.entityName = entityRegistryName(entity);
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        DeathCause cause = DeathCause.of(source);
        eventInfo.killerPlayer = cause.killerPlayer;
        eventInfo.killedBy = cause.killedBy;

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}
