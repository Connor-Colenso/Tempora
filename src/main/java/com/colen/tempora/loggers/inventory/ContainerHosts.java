package com.colen.tempora.loggers.inventory;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.loggers.inventory.compat.AE2ContainerHosts;

import cpw.mods.fml.common.Loader;

/**
 * Remembers what each open container belongs to. Hosts are recorded the moment a container is opened, keyed by the
 * container itself, so they cannot go stale the way "the last GUI this player opened" can. Server thread only.
 */
public final class ContainerHosts {

    // FML's openGui only means something when there really is a block entity there; item GUIs pass slot numbers or
    // the player's position. Anything further than this from the player is not what they are looking at either.
    private static final double MAX_GUI_DISTANCE_SQ = 64 * 64;

    private static final boolean AE2_LOADED = Loader.isModLoaded("appliedenergistics2");

    private static final Map<Container, ContainerHost> HOSTS = new WeakHashMap<>();

    private ContainerHosts() {}

    /** FML openGui finished. */
    public static void onGuiOpened(EntityPlayer player, @Nullable Container before, World world, int x, int y, int z) {
        if (!(player instanceof EntityPlayerMP playerMP) || player instanceof FakePlayer) return;

        Container opened = openedSince(playerMP, before);
        if (opened == null || world == null) return;
        if (world == player.worldObj && player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > MAX_GUI_DISTANCE_SQ) return;
        if (!world.blockExists(x, y, z) || world.getTileEntity(x, y, z) == null) return;

        register(opened, ContainerHost.block(world, x, y, z));
    }

    /** A block's onBlockActivated finished, any container opened during it belongs to that block. */
    public static void onBlockActivated(EntityPlayerMP player, @Nullable Container before, World world, int x, int y,
        int z) {
        Container opened = openedSince(player, before);
        if (opened != null) register(opened, ContainerHost.block(world, x, y, z));
    }

    /**
     * A packet that can open a container has been processed. Block GUIs were already recorded by
     * {@link #onBlockActivated}, so a container that opened during a right click without one came from the held item.
     */
    static void onPacketProcessed(EntityPlayerMP player, Packet packet, @Nullable Container before,
        @Nullable ItemStack heldBefore) {
        Container opened = openedSince(player, before);
        if (opened == null || HOSTS.containsKey(opened)) return;

        if (packet instanceof C08PacketPlayerBlockPlacement placement) {
            if (heldBefore != null) {
                register(opened, ContainerHost.item(heldBefore));
            } else if (placement.func_149568_f() != 255) {
                // Opened by something reacting to the click itself, e.g. an interact event handler.
                register(
                    opened,
                    ContainerHost.block(
                        player.worldObj,
                        placement.func_149576_c(),
                        placement.func_149571_d(),
                        placement.func_149570_e()));
            }
        } else if (packet instanceof C02PacketUseEntity useEntity) {
            Entity entity = useEntity.func_149564_a(player.worldObj);
            if (entity != null) register(opened, ContainerHost.entity(entity));
        } else if (packet instanceof C0BPacketEntityAction && player.ridingEntity != null) {
            // Opening a ridden horse's inventory.
            register(opened, ContainerHost.entity(player.ridingEntity));
        }
    }

    static boolean opensContainers(Packet packet) {
        return packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C02PacketUseEntity
            || packet instanceof C0BPacketEntityAction;
    }

    /**
     * What this container belongs to. Falls back to the first block or entity inventory it shows, and failing that to
     * the player's position under the container's class name.
     */
    static ContainerHost resolve(EntityPlayerMP player, Container container, @Nullable WorldStorage firstStorage) {
        if (AE2_LOADED) {
            ContainerHost ae2Host = AE2ContainerHosts.resolve(container);
            if (ae2Host != null) return ae2Host;
        }

        ContainerHost host = HOSTS.get(container);
        if (host != null) return host;

        if (firstStorage != null) return firstStorage.asHost();

        return ContainerHost.unknown(
            container.getClass()
                .getSimpleName());
    }

    /** Whether this container hides storage behind it that its slots do not show, e.g. an AE2 terminal's network. */
    static boolean hasHiddenStorage(Container container) {
        return AE2_LOADED && AE2ContainerHosts.hasHiddenStorage(container);
    }

    private static @Nullable Container openedSince(EntityPlayerMP player, @Nullable Container before) {
        Container now = player.openContainer;
        if (now == null || now == before || now == player.inventoryContainer) return null;
        return now;
    }

    // The first one recorded wins: FML's coordinates and the activated block are both recorded before the end of the
    // packet that opened the container.
    private static void register(Container container, ContainerHost host) {
        if (!HOSTS.containsKey(container)) HOSTS.put(container, host);
    }
}
