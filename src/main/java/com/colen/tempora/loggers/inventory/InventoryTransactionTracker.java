package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.Tempora.LOG;

import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C11PacketEnchantItem;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.TemporaEvents;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;

/**
 * Wraps the handling of each packet a player sends that can move items through an open container (clicks, closing,
 * enchanting, and every mod packet, which is how AE2 terminals and ModularUI GUIs talk to the server) in an
 * {@link InventoryTransaction}. Also notices which packet opened a container, see {@link ContainerHosts}.
 * <p>
 * All packets are handled one at a time on the server thread, so a single active transaction is enough. Every hook
 * here is guarded, including against a LinkageError from a mod whose classes changed (AE2's, through
 * AE2ContainerHosts), so a packet is always handled.
 */
public final class InventoryTransactionTracker {

    // NEI's packets only do cheat/creative things (giving items, setting ghost slots), none of which is a real
    // transfer to or from the open container.
    private static final String NEI_CHANNEL = "NEI";

    // Mode of a creative middle click in C0EPacketClickWindow.
    private static final int CLONE_CLICK_MODE = 3;

    private static @Nullable InventoryTransaction active;

    private InventoryTransactionTracker() {}

    /** Returned by {@link #beforePacket} and handed back to {@link #afterPacket}. */
    public static final class PacketToken {

        private final EntityPlayerMP player;
        private final Packet packet;
        private final Container containerBefore;
        private final @Nullable ItemStack heldBefore;
        private final @Nullable InventoryTransaction transaction;

        private PacketToken(EntityPlayerMP player, Packet packet, Container containerBefore,
            @Nullable ItemStack heldBefore, @Nullable InventoryTransaction transaction) {
            this.player = player;
            this.packet = packet;
            this.containerBefore = containerBefore;
            this.heldBefore = heldBefore;
            this.transaction = transaction;
        }
    }

    public static @Nullable PacketToken beforePacket(EntityPlayerMP player, Packet packet) {
        try {
            if (player == null || player instanceof FakePlayer || !TemporaEvents.inventoryLogger.isEnabledByConfig()) {
                return null;
            }

            Container container = player.openContainer;

            InventoryTransaction transaction = null;
            if (active == null && container != null && container != player.inventoryContainer && movesItems(packet)) {
                transaction = new InventoryTransaction(player, container, isCreativeCopy(packet));
                active = transaction;
            }

            if (transaction == null && !ContainerHosts.opensContainers(packet)) return null;

            // Only used to name a container the held item opens, so the stack itself is enough.
            ItemStack held = packet instanceof C08PacketPlayerBlockPlacement ? player.getCurrentEquippedItem() : null;
            return new PacketToken(player, packet, container, held, transaction);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora inventory logging failed before a packet was handled; the packet is unaffected.", e);
            return null;
        }
    }

    public static void afterPacket(@Nullable PacketToken token) {
        if (token == null) return;

        try {
            if (token.transaction != null) {
                active = null;
                token.transaction.finish();
            }

            if (ContainerHosts.opensContainers(token.packet)) {
                ContainerHosts.onPacketProcessed(token.player, token.packet, token.containerBefore, token.heldBefore);
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora inventory logging failed after a packet was handled; the packet is unaffected.", e);
        } finally {
            if (active == token.transaction) active = null;
        }
    }

    /**
     * A container can also be closed without the player asking, e.g. by walking away from it. Closing can still move
     * items (a crafting table drops what is left in its grid), so it gets a transaction of its own unless it is part of
     * a packet that already has one.
     */
    public static @Nullable InventoryTransaction beforeClose(EntityPlayerMP player) {
        try {
            if (active != null || player instanceof FakePlayer || !TemporaEvents.inventoryLogger.isEnabledByConfig()) {
                return null;
            }

            Container container = player.openContainer;
            if (container == null || container == player.inventoryContainer) return null;

            active = new InventoryTransaction(player, container, false);
            return active;
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora inventory logging failed before a container was closed; closing is unaffected.", e);
            return null;
        }
    }

    public static void afterClose(@Nullable InventoryTransaction transaction) {
        if (transaction == null) return;

        try {
            active = null;
            transaction.finish();
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora inventory logging failed after a container was closed; closing is unaffected.", e);
        } finally {
            if (active == transaction) active = null;
        }
    }

    /** Another mod reported moving items for this player, see {@link InventoryLogger#logReportedChange}. */
    static void onReportedChange(EntityPlayer player, ItemKey item, long change) {
        InventoryTransaction transaction = active;
        if (transaction != null && transaction.isFor(player)) transaction.recordReportedChange(item, change);
    }

    /** An item entity appeared in the world, which during a transaction means something was dropped or thrown out. */
    public static void onItemEntitySpawned(EntityItem entityItem) {
        // In singleplayer the client thread spawns item entities too, and must never touch the server's transaction.
        if (entityItem.worldObj == null || entityItem.worldObj.isRemote) return;

        InventoryTransaction transaction = active;
        if (transaction == null) return;

        // Only brand new items; item entities loaded with a chunk also join the world.
        if (entityItem.age != 0) return;

        ItemStack stack = entityItem.getEntityItem();
        if (stack != null) transaction.recordSpawnedItem(stack);
    }

    private static boolean movesItems(Packet packet) {
        if (packet instanceof FMLProxyPacket modPacket) return !NEI_CHANNEL.equals(modPacket.channel());

        return packet instanceof C0EPacketClickWindow || packet instanceof C0DPacketCloseWindow
            || packet instanceof C11PacketEnchantItem
            || packet instanceof C17PacketCustomPayload;
    }

    private static boolean isCreativeCopy(Packet packet) {
        return packet instanceof C0EPacketClickWindow click && click.func_149542_h() == CLONE_CLICK_MODE;
    }
}
