package com.colen.tempora.loggers.entity_spawn;

import static com.colen.tempora.TemporaUtils.isClientSide;

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

import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.mixin_interfaces.IEntityMixin;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntitySpawnLogger extends GenericPositionalLogger<EntitySpawnQueueElement> {

    @Override
    public @NotNull EntitySpawnQueueElement getQueueElementInstance() {
        return new EntitySpawnQueueElement();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (EntitySpawnQueueElement esqe : transparentEventsToRenderInWorld) {
            Entity entity = EntityList.createEntityByName(esqe.entityName, Minecraft.getMinecraft().theWorld);

            // Render mob
            RenderUtils.renderEntityInWorld(entity, esqe.x, esqe.y, esqe.z, esqe.rotationYaw, esqe.rotationPitch);

            // Render bounding box (optional, matches location)
            RenderUtils.renderEntityAABBInWorld(entity, esqe.x, esqe.y, esqe.z, 0, 0, 1.0);
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

        EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.entity.posX;
        queueElement.y = event.entity.posY;
        queueElement.z = event.entity.posZ;
        queueElement.dimensionID = event.entity.dimension;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.entityName = event.entity.getCommandSenderName();
        queueElement.entityUUID = event.entity.getUniqueID()
            .toString();

        queueElement.rotationYaw = event.entity.rotationYaw;
        queueElement.rotationPitch = event.entity.rotationPitch;

        queueEvent(queueElement);
    }

}
