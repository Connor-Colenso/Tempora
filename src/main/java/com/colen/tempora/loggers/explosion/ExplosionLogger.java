package com.colen.tempora.loggers.explosion;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
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

    // Closest player, the exploder often isn't one
    @Override
    protected String playerColumn() {
        return "closestPlayerUUID";
    }

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
        // Whoever lit the TNT or the creeper itself, not the TNT entity
        Entity exploder = explosion.getExplosivePlacedBy() != null ? explosion.getExplosivePlacedBy()
            : explosion.exploder;

        logExplosion(
            event.world,
            explosion.explosionX,
            explosion.explosionY,
            explosion.explosionZ,
            // doExplosionA doubles explosionSize while it hurts entities and fires this in between, so a creeper (3)
            // would log as 6
            explosion.explosionSize / 2f,
            exploder,
            // Explosions that don't break blocks (isSmoking false, e.g. creepers, ghasts and withers with mobGriefing
            // off) still report every position reached, but vanilla leaves them all standing
            explosion.isSmoking ? destroyedBlocks(event.world, explosion.affectedBlockPositions)
                : Collections.emptyList());
    }

    // Logs an explosion. exploder is whatever caused it if known, players stored by UUID and anything else by
    // EntityList name (e.g. Creeper) so chat can name it. affectedBlocks are the positions it destroyed if known
    public void logExplosion(World world, double x, double y, double z, float power, @Nullable Entity exploder,
        @Nullable List<ChunkPosition> affectedBlocks) {
        if (!isEnabledByConfig) return;

        ExplosionEventInfo eventInfo = new ExplosionEventInfo();
        eventInfo.power = power;

        if (exploder instanceof EntityPlayer) {
            eventInfo.exploder = exploder.getUniqueID()
                .toString();
        } else if (exploder != null) {
            eventInfo.exploder = entityRegistryName(exploder);
        } else {
            eventInfo.exploder = UNKNOWN_CAUSE;
        }

        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        eventInfo.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        // Whole blocks, same as block changes
        eventInfo.closestPlayerDistance = closestPlayer != null ? Math.round(closestPlayer.getDistance(x, y, z)) : -1;

        eventInfo.affectedBlockCoordinates = ChunkPositionUtils
            .encodePositions(affectedBlocks == null ? Collections.emptyList() : affectedBlocks);

        logEvent(eventInfo, world.provider.dimensionId, x, y, z);
    }

    // Vanilla's affected positions are everything the blast reached air included, so a creeper in the open would count
    // dozens of blocks it never touched. Detonate fires before anything gets removed, so whatever's there now is what
    // it destroys
    private static List<ChunkPosition> destroyedBlocks(World world, List<ChunkPosition> affectedBlocks) {
        List<ChunkPosition> destroyed = new ArrayList<>();
        for (ChunkPosition pos : affectedBlocks) {
            if (!world.isAirBlock(pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ)) destroyed.add(pos);
        }
        return destroyed;
    }

}
