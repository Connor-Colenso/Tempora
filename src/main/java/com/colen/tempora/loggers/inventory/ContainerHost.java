package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * What an open container belongs to, so changes that cannot be pinned to a specific inventory slot (AE2 networks,
 * ModularUI machine slots, crafting grids...) are logged against it. A block, an entity, or an item the player is
 * holding, in which case the change is logged at the player's position.
 */
public final class ContainerHost {

    private enum Kind {
        BLOCK,
        ENTITY,
        PLAYER
    }

    private final Kind kind;
    private final @Nullable World world;
    private final int x, y, z;
    private final @Nullable Entity entity;
    private @Nullable String name;

    private ContainerHost(Kind kind, @Nullable World world, int x, int y, int z, @Nullable Entity entity,
        @Nullable String name) {
        this.kind = kind;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entity = entity;
        this.name = name;
    }

    public static ContainerHost block(World world, int x, int y, int z) {
        return new ContainerHost(Kind.BLOCK, world, x, y, z, null, null);
    }

    /** A block, but named by the caller, e.g. an AE2 terminal rather than the cable bus it sits on. */
    public static ContainerHost block(World world, int x, int y, int z, @Nullable String name) {
        return new ContainerHost(Kind.BLOCK, world, x, y, z, null, name);
    }

    /** A block entity, named after a custom inventory name (e.g. a renamed chest) when it has one. */
    public static ContainerHost tileEntity(TileEntity tileEntity) {
        World world = tileEntity.getWorldObj();
        int x = tileEntity.xCoord, y = tileEntity.yCoord, z = tileEntity.zCoord;
        String name = blockName(world, x, y, z, tileEntity instanceof IInventory inventory ? inventory : null);
        return new ContainerHost(Kind.BLOCK, world, x, y, z, null, name);
    }

    public static ContainerHost entity(Entity entity) {
        return new ContainerHost(Kind.ENTITY, null, 0, 0, 0, entity, null);
    }

    /** A container opened from an item, e.g. a wireless terminal or a backpack. */
    public static ContainerHost item(ItemStack stack) {
        return new ContainerHost(Kind.PLAYER, null, 0, 0, 0, null, stack.getDisplayName());
    }

    /** Nothing better is known, so the change is logged at the player under the container's class name. */
    public static ContainerHost unknown(String name) {
        return new ContainerHost(Kind.PLAYER, null, 0, 0, 0, null, name);
    }

    public String name() {
        if (name == null) {
            name = switch (kind) {
                case BLOCK -> blockName(world, x, y, z, null);
                case ENTITY -> entity.getCommandSenderName();
                case PLAYER -> "?";
            };
        }
        return name;
    }

    public int dimension(EntityPlayer player) {
        return switch (kind) {
            case BLOCK -> world.provider.dimensionId;
            case ENTITY -> entity.dimension;
            case PLAYER -> player.dimension;
        };
    }

    public double x(EntityPlayer player) {
        return switch (kind) {
            case BLOCK -> x;
            case ENTITY -> entity.posX;
            case PLAYER -> player.posX;
        };
    }

    public double y(EntityPlayer player) {
        return switch (kind) {
            case BLOCK -> y;
            case ENTITY -> entity.posY;
            case PLAYER -> player.posY;
        };
    }

    public double z(EntityPlayer player) {
        return switch (kind) {
            case BLOCK -> z;
            case ENTITY -> entity.posZ;
            case PLAYER -> player.posZ;
        };
    }

    /**
     * The name to show for a block: an inventory's custom name (e.g. a renamed chest), otherwise the name of the
     * block's item.
     */
    public static String blockName(World world, int x, int y, int z, @Nullable IInventory inventory) {
        if (inventory != null && inventory.hasCustomInventoryName()) {
            return inventory.getInventoryName();
        }

        Block block = world.getBlock(x, y, z);
        try {
            ItemStack pickStack = getPickBlockSafe(block, world, x, y, z);
            if (pickStack != null) return pickStack.getDisplayName();
        } catch (RuntimeException ignored) {
            // Some blocks cannot work out their item away from the client, fall through to the block's own name.
        }
        return block.getLocalizedName();
    }
}
