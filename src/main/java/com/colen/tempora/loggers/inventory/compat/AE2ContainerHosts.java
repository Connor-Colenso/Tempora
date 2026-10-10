package com.colen.tempora.loggers.inventory.compat;

import javax.annotation.Nullable;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.loggers.inventory.ContainerHost;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerMEMonitorable;

/**
 * Names AE2 containers properly. Generic tracking already places them, but a terminal on a cable would otherwise be
 * named after the cable bus, and a wireless terminal after whatever block happened to be clicked. Only touch this class
 * when AE2 is loaded.
 */
public final class AE2ContainerHosts {

    private AE2ContainerHosts() {}

    public static @Nullable ContainerHost resolve(Container container) {
        if (!(container instanceof AEBaseContainer aeContainer)) return null;

        try {
            return resolve(aeContainer);
        } catch (RuntimeException e) {
            // A half-built open context or an odd part; the generic tracking still places the container.
            return null;
        }
    }

    private static @Nullable ContainerHost resolve(AEBaseContainer aeContainer) {
        Object target = aeContainer.getTarget();

        if (target instanceof IPart part) {
            ContainerOpenContext context = aeContainer.getOpenContext();
            TileEntity tile = context == null ? null : context.getTile();
            if (tile == null) return null;

            ItemStack partStack = part.getItemStack(PartItemStack.Network);
            return ContainerHost.block(
                tile.getWorldObj(),
                tile.xCoord,
                tile.yCoord,
                tile.zCoord,
                partStack == null ? null : partStack.getDisplayName());
        }

        if (target instanceof IGuiItemObject itemObject && itemObject.getItemStack() != null) {
            return ContainerHost.item(itemObject.getItemStack());
        }

        // Block entities (ME chest, interface, drive...) are placed and named fine by the generic tracking.
        return null;
    }

    /** Terminals show the network's contents without slots, so items can come and go with no slot changing. */
    public static boolean hasHiddenStorage(Container container) {
        return container instanceof ContainerMEMonitorable;
    }
}
