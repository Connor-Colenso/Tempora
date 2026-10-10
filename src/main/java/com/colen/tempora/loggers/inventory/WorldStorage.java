package com.colen.tempora.loggers.inventory;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.mixins.accessors.InventoryLargeChestAccessor;

/**
 * An inventory that exists in the world on its own, i.e. a block entity or an entity such as a chest minecart, as
 * opposed to the player's inventory, a crafting grid, or a mod's wrapper around storage we cannot see into.
 * Container slots backed by one of these can be compared directly, so their changes are logged at their exact position.
 */
public final class WorldStorage {

    private final @Nullable TileEntity tileEntity;
    private final @Nullable Entity entity;

    private WorldStorage(@Nullable TileEntity tileEntity, @Nullable Entity entity) {
        this.tileEntity = tileEntity;
        this.entity = entity;
    }

    /** The world storage behind this slot, or null when the slot is backed by anything else. */
    public static @Nullable WorldStorage of(Slot slot) {
        return of(slot.inventory, slot.getSlotIndex());
    }

    private static @Nullable WorldStorage of(IInventory inventory, int index) {
        if (inventory instanceof TileEntity tileEntity) return new WorldStorage(tileEntity, null);
        if (inventory instanceof Entity entity) return new WorldStorage(null, entity);

        // A double chest is two chests glued together, log against the half the slot actually reads from.
        if (inventory instanceof InventoryLargeChest largeChest) {
            InventoryLargeChestAccessor halves = (InventoryLargeChestAccessor) largeChest;
            IInventory upper = halves.tempora$getUpperChest();
            int upperSize = upper.getSizeInventory();
            return index < upperSize ? of(upper, index) : of(halves.tempora$getLowerChest(), index - upperSize);
        }

        return null;
    }

    public int dimension() {
        if (tileEntity != null) return tileEntity.getWorldObj().provider.dimensionId;
        return entity.dimension;
    }

    public double x() {
        return tileEntity != null ? tileEntity.xCoord : entity.posX;
    }

    public double y() {
        return tileEntity != null ? tileEntity.yCoord : entity.posY;
    }

    public double z() {
        return tileEntity != null ? tileEntity.zCoord : entity.posZ;
    }

    public String name() {
        if (tileEntity != null) {
            return ContainerHost.blockName(
                tileEntity.getWorldObj(),
                tileEntity.xCoord,
                tileEntity.yCoord,
                tileEntity.zCoord,
                tileEntity instanceof IInventory inventory ? inventory : null);
        }
        return entity.getCommandSenderName();
    }

    public ContainerHost asHost() {
        if (tileEntity != null) {
            return ContainerHost
                .block(tileEntity.getWorldObj(), tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        }
        return ContainerHost.entity(entity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldStorage other)) return false;
        return tileEntity == other.tileEntity && entity == other.entity;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(tileEntity) * 31 + System.identityHashCode(entity);
    }
}
