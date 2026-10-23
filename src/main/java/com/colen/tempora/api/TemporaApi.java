package com.colen.tempora.api;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.inventory.ContainerHost;
import com.colen.tempora.utils.GenericUtils;

/**
 * Lets other mods report things Tempora cannot see by itself, so they are logged and looked up like everything else.
 * Typical cases: items moved in or out of storage without a GUI (drawers, barrels), blocks broken or placed without
 * Forge's events firing, explosions that are not vanilla ones, and blocks written straight into chunk storage.
 * <p>
 * Each method feeds the logger that records that kind of event, and follows that logger's config and region rules just
 * like the events Tempora picks up itself. A call does nothing if that logger is turned off, if Tempora is not running
 * (on a client, say), or if it is not made on the server thread, so it is safe to call on every action.
 * <p>
 * Tempora is optional for most mods: compile against its {@code api} jar and only call this when it is loaded, e.g.
 *
 * <pre>
 * {@code
 * if (Loader.isModLoaded(TemporaApi.MODID)) {
 *     TemporaApi.logItemsAdded(player, insertedStack, inserted, this);
 * }
 * }
 * </pre>
 */
public final class TemporaApi {

    public static final String MODID = "tempora";

    private TemporaApi() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Inventories
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A player put items into a block's storage, e.g. by clicking a drawer. {@code amount} is how many went in; the
     * stack's own size is ignored, only its item, metadata and NBT are used.
     */
    public static void logItemsAdded(EntityPlayer player, ItemStack item, long amount, TileEntity container) {
        if (player == null || item == null || amount <= 0 || container == null) return;
        if (!onServer(container.getWorldObj())) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, amount, ContainerHost.tileEntity(container));
    }

    /**
     * A player took items out of a block's storage. See
     * {@link #logItemsAdded(EntityPlayer, ItemStack, long, TileEntity)}.
     */
    public static void logItemsRemoved(EntityPlayer player, ItemStack item, long amount, TileEntity container) {
        if (player == null || item == null || amount <= 0 || container == null) return;
        if (!onServer(container.getWorldObj())) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, -amount, ContainerHost.tileEntity(container));
    }

    /** As {@link #logItemsAdded(EntityPlayer, ItemStack, long, TileEntity)}, for storage with no block entity. */
    public static void logItemsAdded(EntityPlayer player, ItemStack item, long amount, World world, int x, int y,
        int z) {
        if (player == null || item == null || amount <= 0) return;
        if (!onServer(world)) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, amount, ContainerHost.block(world, x, y, z));
    }

    /** As {@link #logItemsRemoved(EntityPlayer, ItemStack, long, TileEntity)}, for storage with no block entity. */
    public static void logItemsRemoved(EntityPlayer player, ItemStack item, long amount, World world, int x, int y,
        int z) {
        if (player == null || item == null || amount <= 0) return;
        if (!onServer(world)) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, -amount, ContainerHost.block(world, x, y, z));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Blocks
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A block is about to be broken. Call this before removing it, so the block and its tile entity can still be read.
     * {@code player} may be null when nobody in particular broke it.
     */
    public static void logBlockBreak(@Nullable EntityPlayer player, World world, int x, int y, int z) {
        if (!onServer(world)) return;

        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return;

        TemporaEvents.playerBlockBreakLogger
            .logBlockBreak(player, world, x, y, z, block, world.getBlockMetadata(x, y, z));
    }

    /**
     * A block was placed. Call this after it is in the world. {@code player} may be null when nobody in particular
     * placed it.
     */
    public static void logBlockPlace(@Nullable EntityPlayer player, World world, int x, int y, int z) {
        if (!onServer(world)) return;

        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return;

        TemporaEvents.playerBlockPlaceLogger.logBlockPlace(player, world, x, y, z, block);
    }

    /**
     * A block changed without going through {@code Chunk.func_150807_a}, which Tempora watches for every other block
     * change, e.g. it was written straight into the chunk's storage. Call this after the change, with what was there
     * before: the old block, its metadata, and its tile entity's NBT if it had one.
     */
    public static void logBlockChange(World world, int x, int y, int z, Block before, int beforeMetadata,
        @Nullable NBTTagCompound beforeTileEntityNBT) {
        if (before == null || !onServer(world)) return;

        TemporaEvents.blockChangeLogger.logBlockChange(world, x, y, z, before, beforeMetadata, beforeTileEntityNBT);
    }

    /**
     * An explosion that did not fire Forge's {@code ExplosionEvent}, e.g. one that is not a vanilla {@code Explosion}.
     * {@code exploder} is whatever is responsible, if known: a living entity is stored by UUID, anything else by its
     * class name. {@code destroyedBlocks} are the positions it destroyed, if known.
     */
    public static void logExplosion(World world, double x, double y, double z, float power, @Nullable Entity exploder,
        @Nullable List<ChunkPosition> destroyedBlocks) {
        if (!onServer(world)) return;

        TemporaEvents.explosionLogger.logExplosion(world, x, y, z, power, exploder, destroyedBlocks);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Players
    // ---------------------------------------------------------------------------------------------------------------

    /** A player used an item, or their empty hand if {@code item} is null. */
    public static void logItemUse(EntityPlayer player, @Nullable ItemStack item) {
        if (player == null || !onServer(player.worldObj)) return;

        TemporaEvents.itemUseLogger.logItemUse(player, item);
    }

    /** A player ran a command that did not go through Forge's {@code CommandEvent}. */
    public static void logCommand(EntityPlayer player, String command, String... arguments) {
        if (player == null || command == null || !onServer(player.worldObj)) return;

        TemporaEvents.commandLogger.logCommand(player, command, arguments == null ? new String[0] : arguments);
    }

    /** Where a player is right now, e.g. straight after a mod teleported them. */
    public static void logPlayerPosition(EntityPlayer player) {
        if (player == null || !onServer(player.worldObj)) return;

        TemporaEvents.playerPositionLogger.logPlayerPosition(player);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------------------------------------------------

    /** An entity appeared in the world. Each entity is only logged once, however often this is called for it. */
    public static void logEntitySpawn(Entity entity) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entitySpawnLogger.logEntitySpawn(entity);
    }

    /**
     * A mob died without Forge's {@code LivingDeathEvent} firing, e.g. it was removed with {@code setDead()}.
     * {@code source} says what killed it, if known.
     */
    public static void logEntityDeath(EntityLivingBase entity, @Nullable DamageSource source) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entityDeathLogger.logEntityDeath(entity, source);
    }

    /** Where an entity is right now. */
    public static void logEntityPosition(Entity entity) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entityPositionLogger.logEntityPosition(entity);
    }

    // Loggers read the world when building an event, so only ever on the server thread.
    private static boolean onServer(@Nullable World world) {
        return world != null && !world.isRemote && GenericUtils.isServerSide();
    }
}
