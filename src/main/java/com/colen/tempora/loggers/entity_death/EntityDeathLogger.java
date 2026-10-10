package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityDeathLogger extends GenericPositionalLogger<EntityDeathEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_DEATH;
    }

    @Override
    public @NotNull String getShortName() {
        return "death";
    }

    @Override
    public @NotNull EntityDeathEventInfo newEventInfo() {
        return new EntityDeathEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (EntityDeathEventInfo bcEventInfo : transparentEventsToRenderInWorld) {
            Entity entity = EntityList
                .createEntityByName(bcEventInfo.nameOfDeadEntity, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(
                entity,
                bcEventInfo.x,
                bcEventInfo.y,
                bcEventInfo.z,
                bcEventInfo.rotationYaw,
                bcEventInfo.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, bcEventInfo.x, bcEventInfo.y, bcEventInfo.z, 1.0, 0, 0);
        }
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

    /** Logs a mob dying, and what killed it. Players are not logged here, this is for mobs only. */
    public void logEntityDeath(EntityLivingBase entity, @Nullable DamageSource source) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;

        EntityDeathEventInfo eventInfo = new EntityDeathEventInfo();
        eventInfo.nameOfDeadEntity = EntityList.getEntityString(entity);
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        // Get what killed it.
        Entity trueSource = source == null ? null : source.getEntity();
        if (trueSource != null) {
            if (trueSource instanceof EntityPlayerMP player) {
                // This is specific for players
                eventInfo.killedBy = player.getUniqueID()
                    .toString();
            } else {
                // For non-player entities
                eventInfo.killedBy = "[" + trueSource.getClass()
                    .getSimpleName() + "]";
            }
        } else if (source != null) {
            eventInfo.killedBy = "[" + source.damageType + "]";
        } else {
            eventInfo.killedBy = UNKNOWN_CAUSE;
        }

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}
