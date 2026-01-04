package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.colen.tempora.enums.LoggerEventType;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
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

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.LastInvPos;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Todo fix drag and drop not logging correctly.
public class InventoryLogger extends GenericPositionalLogger<InventoryQueueElement> {

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.None;
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.InventoryLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        List<InventoryQueueElement> sortedList = getSortedLatestEventsByDistance(transparentEventsToRenderInWorld, e);

        Minecraft mc = Minecraft.getMinecraft();

        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        for (InventoryQueueElement iqe : sortedList) {
            GL11.glPushMatrix();
            GL11.glTranslated(iqe.x, iqe.y, iqe.z);
            RenderUtils.renderRegion(0, 0, 0, 1, 1, 1, 0.655, 0.125, 0.8);
            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
    }

    // This only exists so debug breakpoints can be used, as we are no longer inside of the mixin itself.
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
                    Tempora.inventoryLogger.playerInteractedWithInventory(
                        player,
                        delta,
                        after == null ? before : after,
                        dir,
                        tileEntity,
                        s.inventory,
                        container);
                } else {
                    Tempora.inventoryLogger.playerInteractedWithInventory(
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

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("containerName", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("interactionType", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("itemID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("itemMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("stacksize", "INTEGER", "NOT NULL DEFAULT -1"));
    }

    public InventoryLogger() {
        registerLogger(this);
    }

    @Override
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();
        while (resultSet.next()) {
            InventoryQueueElement queueElement = new InventoryQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.containerName = resultSet.getString("containerName");
            queueElement.playerUUID = resultSet.getString("playerUUID");
            queueElement.interactionType = resultSet.getInt("interactionType");
            queueElement.itemId = resultSet.getInt("itemID");
            queueElement.itemMetadata = resultSet.getInt("itemMetadata");
            queueElement.stackSize = resultSet.getInt("stacksize");
            eventList.add(queueElement);
        }
        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<InventoryQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (containerName, interactionType, itemId, itemMetadata, playerUUID, stacksize, eventID, x, y, z, dimensionID, timestamp, versionID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (InventoryQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setString(index++, queueElement.containerName);
                pstmt.setInt(index++, queueElement.interactionType);
                pstmt.setInt(index++, queueElement.itemId);
                pstmt.setInt(index++, queueElement.itemMetadata);
                pstmt.setString(index++, queueElement.playerUUID);
                pstmt.setInt(index++, queueElement.stackSize);
                DatabaseUtils.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    public void playerInteractedWithInventory(EntityPlayer playerMP, int delta, ItemStack itemStack, Direction dir,
        TileEntity tileEntity, IInventory inventory, Container container) {
        if (itemStack == null || delta == 0) return; // Nothing to log
        if (dir == Direction.OUT_OF_PLAYER || dir == Direction.IN_TO_PLAYER) return;

        ItemStack copyStack = itemStack.copy();
        copyStack.stackSize = Math.abs(delta);

        InventoryQueueElement queueElement = new InventoryQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();

        if (inventory instanceof InventoryPlayer) {
            queueElement.containerName = inventory.getInventoryName();
            queueElement.x = playerMP.posX;
            queueElement.y = playerMP.posY;
            queueElement.z = playerMP.posZ;
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

                queueElement.containerName = pickStack.getDisplayName();
                queueElement.x = tileEntity.xCoord;
                queueElement.y = tileEntity.yCoord;
                queueElement.z = tileEntity.zCoord;
            } else if (inventory != null) {
                queueElement.containerName = inventory.getInventoryName();
                queueElement.x = playerMP.posX;
                queueElement.y = playerMP.posY;
                queueElement.z = playerMP.posZ;
            } else {
                queueElement.containerName = container.getClass()
                    .getSimpleName();
                queueElement.x = playerMP.posX;
                queueElement.y = playerMP.posY;
                queueElement.z = playerMP.posZ;
            }
        }

        queueElement.dimensionId = playerMP.dimension;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.playerUUID = playerMP.getUniqueID()
            .toString();
        queueElement.interactionType = dir.getDbId();
        queueElement.itemId = Item.getIdFromItem(copyStack.getItem());
        queueElement.itemMetadata = copyStack.getItemDamage();
        queueElement.stackSize = copyStack.stackSize;

        queueEvent(queueElement);
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

        InventoryQueueElement queueElement = new InventoryQueueElement();

        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = dim;

        queueElement.containerName = containerName;
        queueElement.timestamp = System.currentTimeMillis();
        queueElement.playerUUID = playerMP.getUniqueID()
            .toString();
        queueElement.interactionType = dir.getDbId();
        queueElement.itemId = Item.getIdFromItem(stack.getItem());
        queueElement.itemMetadata = stack.getItemDamage();
        queueElement.stackSize = stack.stackSize;

        queueEvent(queueElement);
    }

}
