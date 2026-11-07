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
 * For other mods to report stuff Tempora can't see by itself, so it gets logged and looked up like everything else.
 * Things like items going in/out of storage without a GUI (Storage Drawers, AE2 interfaces), blocks broken or placed
 * without Forge events firing, explosions that aren't vanilla ones and blocks written straight into chunk storage
 * <p>
 * Each call goes through the matching logger's config and region rules, same as anything Tempora picks up itself. Does
 * nothing if that logger is off, Tempora isn't running (client side) or it's called off the server thread, so it's fine
 * to call on every action
 * <p>
 * Tempora is optional, compile against the {@code api} jar and only call this when it's loaded
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

    // Inventories

    /**
     * Player put items into a block's storage, like clicking a drawer. {@code amount} is how many went in, the stack's
     * own size is ignored and only its item, meta and NBT are used
     */
    public static void logItemsAdded(EntityPlayer player, ItemStack item, long amount, TileEntity container) {
        if (player == null || item == null || amount <= 0 || container == null) return;
        if (!onServer(container.getWorldObj())) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, amount, ContainerHost.tileEntity(container));
    }

    /**
     * Player took items out of a block's storage, see {@link #logItemsAdded(EntityPlayer, ItemStack, long, TileEntity)}
     */
    public static void logItemsRemoved(EntityPlayer player, ItemStack item, long amount, TileEntity container) {
        if (player == null || item == null || amount <= 0 || container == null) return;
        if (!onServer(container.getWorldObj())) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, -amount, ContainerHost.tileEntity(container));
    }

    /** Same as {@link #logItemsAdded(EntityPlayer, ItemStack, long, TileEntity)} for storage without a tile entity */
    public static void logItemsAdded(EntityPlayer player, ItemStack item, long amount, World world, int x, int y,
        int z) {
        if (player == null || item == null || amount <= 0) return;
        if (!onServer(world)) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, amount, ContainerHost.block(world, x, y, z));
    }

    /** Same as {@link #logItemsRemoved(EntityPlayer, ItemStack, long, TileEntity)} for storage without a tile entity */
    public static void logItemsRemoved(EntityPlayer player, ItemStack item, long amount, World world, int x, int y,
        int z) {
        if (player == null || item == null || amount <= 0) return;
        if (!onServer(world)) return;

        TemporaEvents.inventoryLogger.logReportedChange(player, item, -amount, ContainerHost.block(world, x, y, z));
    }

    // Blocks

    /**
     * Block is about to be broken. Call before removing it so the block and its TE can still be read. {@code player}
     * can be null if nobody in particular broke it
     */
    public static void logBlockBreak(@Nullable EntityPlayer player, World world, int x, int y, int z) {
        if (!onServer(world)) return;

        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return;

        TemporaEvents.playerBlockBreakLogger
            .logBlockBreak(player, world, x, y, z, block, world.getBlockMetadata(x, y, z));
    }

    /** Block was placed, call after it's in the world. {@code player} can be null if nobody in particular placed it */
    public static void logBlockPlace(@Nullable EntityPlayer player, World world, int x, int y, int z) {
        if (!onServer(world)) return;

        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return;

        TemporaEvents.playerBlockPlaceLogger.logBlockPlace(player, world, x, y, z, block);
    }

    /**
     * Block changed without going through {@code Chunk.func_150807_a}, which is what Tempora hooks for everything else,
     * e.g. written straight into chunk storage. Call after the change with what was there before (old block, meta and
     * TE NBT if it had one)
     */
    public static void logBlockChange(World world, int x, int y, int z, Block before, int beforeMetadata,
        @Nullable NBTTagCompound beforeTileEntityNBT) {
        if (before == null || !onServer(world)) return;

        TemporaEvents.blockChangeLogger.logBlockChange(world, x, y, z, before, beforeMetadata, beforeTileEntityNBT);
    }

    /**
     * Explosion that didn't fire Forge's {@code ExplosionEvent}, e.g. one that isn't a vanilla {@code Explosion}.
     * {@code exploder} is
     * whatever caused it if known, players are stored by UUID and anything else by its EntityList name.
     * {@code destroyedBlocks} are the positions it destroyed if known, minus any that were already air
     */
    public static void logExplosion(World world, double x, double y, double z, float power, @Nullable Entity exploder,
        @Nullable List<ChunkPosition> destroyedBlocks) {
        if (!onServer(world)) return;

        TemporaEvents.explosionLogger.logExplosion(world, x, y, z, power, exploder, destroyedBlocks);
    }

    // Players

    /** Player used an item, or their empty hand if {@code item} is null */
    public static void logItemUse(EntityPlayer player, @Nullable ItemStack item) {
        if (player == null || !onServer(player.worldObj)) return;

        TemporaEvents.itemUseLogger.logItemUse(player, item);
    }

    /** Player ran a command that didn't go through Forge's {@code CommandEvent} */
    public static void logCommand(EntityPlayer player, String command, String... arguments) {
        if (player == null || command == null || !onServer(player.worldObj)) return;

        TemporaEvents.commandLogger.logCommand(player, command, arguments == null ? new String[0] : arguments);
    }

    /** Where a player is right now, e.g. straight after a mod teleported them */
    public static void logPlayerPosition(EntityPlayer player) {
        if (player == null || !onServer(player.worldObj)) return;

        TemporaEvents.playerPositionLogger.logPlayerPosition(player);
    }

    // Entities

    /** Entity appeared in the world. Only logged once per entity however often this gets called */
    public static void logEntitySpawn(Entity entity) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entitySpawnLogger.logEntitySpawn(entity);
    }

    /**
     * Mob died without Forge's {@code LivingDeathEvent} firing, e.g. removed with {@code setDead()}. {@code source} is
     * what killed it if known
     */
    public static void logEntityDeath(EntityLivingBase entity, @Nullable DamageSource source) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entityDeathLogger.logEntityDeath(entity, source);
    }

    /** Where an entity is right now */
    public static void logEntityPosition(Entity entity) {
        if (entity == null || !onServer(entity.worldObj)) return;

        TemporaEvents.entityPositionLogger.logEntityPosition(entity);
    }

    // Loggers read the world when building an event so server thread only. Does nothing while Tempora is switched off,
    // so mods calling this on every action cost nothing in packs that don't use it
    private static boolean onServer(@Nullable World world) {
        return GenericUtils.shouldTemporaRun() && world != null && !world.isRemote && GenericUtils.isServerSide();
    }
}
