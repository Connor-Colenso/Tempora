package com.colen.tempora.mixins;

import static com.colen.tempora.utils.LastInvPos.getTileEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.Tempora;
import com.colen.tempora.logging.loggers.inventory.InventoryLogger;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.NetworkMonitor;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class MixinNetworkMonitor<T extends IAEStack<T>> {

    /* ======================================================== */
    /* Injection points – now one‑liners that forward */
    /* ======================================================== */

    @Inject(method = "injectItems", at = @At("HEAD"))
    private void onInject(T input, Actionable mode, BaseActionSource src, CallbackInfoReturnable<T> cir) {
        handleTransfer(InventoryLogger.Direction.IN_TO_CONTAINER, input, mode, src, cir.getReturnValue());
    }

    @Inject(method = "extractItems", at = @At("HEAD"))
    private void onExtract(T request, Actionable mode, BaseActionSource src, CallbackInfoReturnable<T> cir) {
        handleTransfer(InventoryLogger.Direction.OUT_OF_CONTAINER, request, mode, src, cir.getReturnValue());
    }

    /* ======================================================== */
    /* Core logic – shared by both paths */
    /* ======================================================== */

    @Unique
    private void handleTransfer(InventoryLogger.Direction dir, T stackRaw, Actionable mode, BaseActionSource src,
        T leftoverRaw) {

        if (!stackRaw.isItem()) return;
        if (mode == Actionable.SIMULATE || !src.isPlayer() || !(src instanceof PlayerSource ps)) return;

        /* -- Work out how many items actually moved ----------- */
        IAEItemStack asked = (IAEItemStack) stackRaw;
        IAEItemStack leftover = (IAEItemStack) leftoverRaw;
        long movedCnt = asked.getStackSize() - (leftover == null ? 0 : leftover.getStackSize());
        if (movedCnt <= 0) return;

        /* -- Container name & coordinates --------------------- */
        String containerName = resolveContainerName(ps.via);
        TileEntity tileEntity = getTileEntity(ps.player.getPersistentID());

        /* -- Build MC ItemStack with correct size ------------- */
        ItemStack moved = asked.getItemStack()
            .copy();
        moved.stackSize = (int) movedCnt;

        /* -- Log ---------------------------------------------- */
        if (tileEntity != null) {
            Tempora.inventoryLogger.specialAELogInv(
                dir,
                ps.player,
                moved,
                containerName,
                tileEntity.xCoord,
                tileEntity.yCoord,
                tileEntity.zCoord,
                tileEntity.getWorldObj().provider.dimensionId);
        } else {
            Tempora.inventoryLogger.specialAELogInv(
                dir,
                ps.player,
                moved,
                containerName,
                ps.player.posX,
                ps.player.posY,
                ps.player.posZ,
                ps.player.worldObj.provider.dimensionId);
        }
    }

    /* ======================================================== */
    /* Helpers */
    /* ======================================================== */

    @Unique
    private static String resolveContainerName(Object via) {
        if (via instanceof IPart part) {
            ItemStack partStack = part.getItemStack(PartItemStack.Network);
            return partStack != null ? partStack.getDisplayName()
                : part.getClass()
                    .getSimpleName();
        }
        return via.getClass()
            .getSimpleName();
    }

}
