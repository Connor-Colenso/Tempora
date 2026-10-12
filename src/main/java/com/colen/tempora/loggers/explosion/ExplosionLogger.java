package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.ChunkPositionUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ExplosionLogger extends GenericPositionalLogger<ExplosionEventInfo> {

    @Override
    public @NotNull ExplosionEventInfo newEventInfo() {
        return new ExplosionEventInfo();
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.EXPLOSION;
    }

    @Override
    public @NotNull String getShortName() {
        return "explosion";
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(final @NotNull ExplosionEvent.Detonate event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        Explosion explosion = event.explosion;
        // Whoever lit the TNT, or the creeper itself, rather than the TNT entity.
        Entity exploder = explosion.getExplosivePlacedBy() != null ? explosion.getExplosivePlacedBy()
            : explosion.exploder;

        logExplosion(
            event.world,
            explosion.explosionX,
            explosion.explosionY,
            explosion.explosionZ,
            explosion.explosionSize,
            exploder,
            explosion.affectedBlockPositions);
    }

    /**
     * Logs an explosion. {@code exploder} is whatever is responsible, if known: a living entity is stored by UUID, any
     * other entity by its class name. {@code affectedBlocks} are the positions it destroyed, if known.
     */
    public void logExplosion(World world, double x, double y, double z, float strength, @Nullable Entity exploder,
        @Nullable List<ChunkPosition> affectedBlocks) {
        if (!isEnabledByConfig) return;

        ExplosionEventInfo eventInfo = new ExplosionEventInfo();
        eventInfo.strength = strength;

        if (exploder instanceof EntityLivingBase) {
            eventInfo.exploderUUID = exploder.getUniqueID()
                .toString();
        } else if (exploder != null) {
            eventInfo.exploderUUID = exploder.getClass()
                .getSimpleName();
        } else {
            eventInfo.exploderUUID = UNKNOWN_CAUSE;
        }

        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        eventInfo.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        eventInfo.closestPlayerDistance = closestPlayer != null ? closestPlayer.getDistance(x, y, z) : -1;

        eventInfo.affectedBlockCoordinates = ChunkPositionUtils
            .encodePositions(affectedBlocks == null ? Collections.emptyList() : affectedBlocks);

        logEvent(eventInfo, world.provider.dimensionId, x, y, z);
    }

}
