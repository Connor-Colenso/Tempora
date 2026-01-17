package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.mixin_interfaces.IEntityMixin;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_SPAWN;
    }

    @Override
    public @NotNull EntitySpawnEventInfo newEventInfo() {
        return new EntitySpawnEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (EntitySpawnEventInfo esEventInfo : transparentEventsToRenderInWorld) {
            Entity entity = EntityList.createEntityByName(esEventInfo.entityName, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(entity, esEventInfo.x, esEventInfo.y, esEventInfo.z, esEventInfo.rotationYaw, esEventInfo.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, esEventInfo.x, esEventInfo.y, esEventInfo.z, 0, 0, 1.0);
        }
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entity instanceof EntityPlayerMP) return;
        if (event.entity instanceof EntityItem) return;
        if (event.entity instanceof EntityXPOrb) return;

        IEntityMixin entityMixin = (IEntityMixin) event.entity;
        if (entityMixin.getTempora$HasBeenLogged()) return;

        // Mark as logged, this is persistent with nbt.
        entityMixin.setTempora$HasBeenLogged(true);

        EntitySpawnEventInfo eventInfo = new EntitySpawnEventInfo();
        eventInfo.eventID = UUID.randomUUID()
            .toString();
        eventInfo.x = event.entity.posX;
        eventInfo.y = event.entity.posY;
        eventInfo.z = event.entity.posZ;
        eventInfo.dimensionID = event.entity.dimension;
        eventInfo.timestamp = System.currentTimeMillis();

        eventInfo.entityName = event.entity.getCommandSenderName();
        eventInfo.entityUUID = event.entity.getUniqueID()
            .toString();

        eventInfo.rotationYaw = event.entity.rotationYaw;
        eventInfo.rotationPitch = event.entity.rotationPitch;

        queueEventInfo(eventInfo);
    }

}
