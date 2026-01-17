package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.LastInvPos;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Todo fix drag and drop not logging correctly.
public class InventoryLogger extends GenericPositionalLogger<InventoryEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.INVENTORY;
    }

    @Override
    public @NotNull InventoryEventInfo newEventInfo() {
        return new InventoryEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.None;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        List<InventoryEventInfo> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);

        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * renderEvent.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * renderEvent.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * renderEvent.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        for (InventoryEventInfo iqe : sortedList) {
            GL11.glPushMatrix();
            GL11.glTranslated(iqe.x, iqe.y, iqe.z);
            RenderUtils.renderRegion(0, 0, 0, 1, 1, 1, 0.655, 0.125, 0.8);
            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
    }

    // This only exists so that the debug breakpoints can be used, as we are no longer inside the mixin itself.
    public static void preLogLogic(EntityPlayer player, Container container, List<Slot> inventorySlots,
        Map<Integer, ItemStack> snapshot) {
        for (Slot s : inventorySlots) {
            ItemStack before = snapshot.get(s.slotNumber);
            ItemStack after = s.getStack();

            if (!ItemStack.areItemStacksEqual(before, after)) {
                int beforeCnt = before == null ? 0 : before.stackSize;
                int afterCnt = after == null ? 0 : after.stackSize;
                int delta = afterCnt - beforeCnt; // + = added, − = removed

                Direction dir = (s.inventory instanceof InventoryPlayer)
                    ? (delta > 0 ? Direction.IN_TO_PLAYER : Direction.OUT_OF_PLAYER)
                    : (delta > 0 ? Direction.IN_TO_CONTAINER : Direction.OUT_OF_CONTAINER);

                if (s.inventory instanceof TileEntity tileEntity) {
                    TemporaEvents.inventoryLogger.playerInteractedWithInventory(
                        player,
                        delta,
                        after == null ? before : after,
                        dir,
                        tileEntity,
                        s.inventory,
                        container);
                } else {
                    TemporaEvents.inventoryLogger.playerInteractedWithInventory(
                        player,
                        delta,
                        after == null ? before : after,
                        dir,
                        null,
                        s.inventory,
                        container);
                }

            }
        }
    }

    public void playerInteractedWithInventory(EntityPlayer playerMP, int delta, ItemStack itemStack, Direction dir,
        TileEntity tileEntity, IInventory inventory, Container container) {
        if (itemStack == null || delta == 0) return; // Nothing to log
        if (dir == Direction.OUT_OF_PLAYER || dir == Direction.IN_TO_PLAYER) return;

        ItemStack copyStack = itemStack.copy();
        copyStack.stackSize = Math.abs(delta);

        InventoryEventInfo eventInfo = new InventoryEventInfo();
        eventInfo.eventID = UUID.randomUUID()
            .toString();

        if (inventory instanceof InventoryPlayer) {
            eventInfo.containerName = inventory.getInventoryName();
            eventInfo.x = playerMP.posX;
            eventInfo.y = playerMP.posY;
            eventInfo.z = playerMP.posZ;
        } else {
            // Special GT handling.
            if (container instanceof ModularUIContainer) {
                LastInvPos lastInvPos = LastInvPos.LAST_OPENED.get(playerMP.getUniqueID());
                World world = MinecraftServer.getServer()
                    .worldServerForDimension(lastInvPos.dimId);
                tileEntity = world.getTileEntity(lastInvPos.x, lastInvPos.y, lastInvPos.z);
            }
            // Special AE handling

            if (tileEntity != null) {
                World world = tileEntity.getWorldObj();
                Block block = world.getBlock(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                ItemStack pickStack = getPickBlockSafe(
                    block,
                    world,
                    tileEntity.xCoord,
                    tileEntity.yCoord,
                    tileEntity.zCoord);

                eventInfo.containerName = pickStack.getDisplayName();
                eventInfo.x = tileEntity.xCoord;
                eventInfo.y = tileEntity.yCoord;
                eventInfo.z = tileEntity.zCoord;
            } else if (inventory != null) {
                eventInfo.containerName = inventory.getInventoryName();
                eventInfo.x = playerMP.posX;
                eventInfo.y = playerMP.posY;
                eventInfo.z = playerMP.posZ;
            } else {
                eventInfo.containerName = container.getClass()
                    .getSimpleName();
                eventInfo.x = playerMP.posX;
                eventInfo.y = playerMP.posY;
                eventInfo.z = playerMP.posZ;
            }
        }

        eventInfo.dimensionID = playerMP.dimension;
        eventInfo.timestamp = System.currentTimeMillis();
        eventInfo.playerUUID = playerMP.getUniqueID()
            .toString();
        eventInfo.interactionType = dir.getDbId();
        eventInfo.itemId = Item.getIdFromItem(copyStack.getItem());
        eventInfo.itemMetadata = copyStack.getItemDamage();
        eventInfo.stackSize = copyStack.stackSize;

        queueEventInfo(eventInfo);
    }

    // Never change the values here.
    public enum Direction {

        IN_TO_CONTAINER(0, true),
        IN_TO_PLAYER(1, true),
        OUT_OF_CONTAINER(2, false),
        OUT_OF_PLAYER(3, false);

        private final int dbId;
        private final boolean addition;

        Direction(int dbId, boolean addition) {
            this.dbId = dbId;
            this.addition = addition;
        }

        public int getDbId() {
            return dbId;
        }

        public boolean isAddition() {
            return addition;
        }

        public static Direction fromOrdinal(int ordinal) {
            Direction[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return null;
            }
            return values[ordinal];
        }
    }

    public void specialAELogInv(Direction dir, EntityPlayer playerMP, ItemStack stack, String containerName, double x,
        double y, double z, int dim) {
        if (dir == Direction.OUT_OF_PLAYER || dir == Direction.IN_TO_PLAYER) return;

        InventoryEventInfo eventInfo = new InventoryEventInfo();

        eventInfo.x = x;
        eventInfo.y = y;
        eventInfo.z = z;
        eventInfo.dimensionID = dim;

        eventInfo.containerName = containerName;
        eventInfo.timestamp = System.currentTimeMillis();
        eventInfo.playerUUID = playerMP.getUniqueID()
            .toString();
        eventInfo.interactionType = dir.getDbId();
        eventInfo.itemId = Item.getIdFromItem(stack.getItem());
        eventInfo.itemMetadata = stack.getItemDamage();
        eventInfo.stackSize = stack.stackSize;

        queueEventInfo(eventInfo);
    }

}
