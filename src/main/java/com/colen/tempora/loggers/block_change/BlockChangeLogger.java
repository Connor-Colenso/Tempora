package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.RenderingUtils.CLIENT_EVENT_RENDER_DISTANCE;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.encodeToString;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.StackTraceUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }

    @Override
    public @NotNull String getShortName() {
        return "block";
    }

    // Every setBlock call in the world is far too much data to log everywhere, so by default this only logs inside
    // regions that turn it on.
    @Override
    protected boolean defaultForLogByDefault() {
        return false;
    }

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    // Client side usage only.
    ArrayList<BlockChangeEventInfo> filteredNonTransparentBuffer = new ArrayList<>();
    ArrayList<BlockChangeEventInfo> filteredTransparentBuffer = new ArrayList<>();

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;

        if (player == null) return;

        double maxDistSq = CLIENT_EVENT_RENDER_DISTANCE * CLIENT_EVENT_RENDER_DISTANCE;

        // Clear and pre-size buffers
        filteredNonTransparentBuffer.clear();
        filteredTransparentBuffer.clear();

        filteredNonTransparentBuffer.ensureCapacity(nonTransparentEventsToRenderInWorld.size());
        filteredTransparentBuffer.ensureCapacity(transparentEventsToRenderInWorld.size());

        // --- NON-TRANSPARENT ---
        for (BlockChangeEventInfo event : nonTransparentEventsToRenderInWorld) {
            if (event.dimensionID != player.dimension) continue;
            if (player.getDistanceSq(event.x, event.y, event.z) > maxDistSq) continue;
            filteredNonTransparentBuffer.add(event);
        }

        for (BlockChangeEventInfo bcEventInfo : filteredNonTransparentBuffer) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                bcEventInfo,
                bcEventInfo.beforeBlockID,
                bcEventInfo.beforeMetadata,
                bcEventInfo.beforeEncodedNBT,
                bcEventInfo.closestPlayerUUID,
                this);
        }

        // --- TRANSPARENT ---
        for (BlockChangeEventInfo event : transparentEventsToRenderInWorld) {
            if (event.dimensionID != player.dimension) continue;
            if (player.getDistanceSq(event.x, event.y, event.z) > maxDistSq) continue;
            filteredTransparentBuffer.add(event);
        }

        for (BlockChangeEventInfo bcEventInfo : getSortedLatestEventsByDistance(
            filteredTransparentBuffer,
            renderEvent)) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                bcEventInfo,
                bcEventInfo.beforeBlockID,
                bcEventInfo.beforeMetadata,
                bcEventInfo.beforeEncodedNBT,
                bcEventInfo.closestPlayerUUID,
                this);
        }
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            false,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.None;
    }

    @Override
    public @NotNull BlockChangeEventInfo newEventInfo() {
        return new BlockChangeEventInfo();
    }

    // Not convinced multithreaded safety is needed here.
    private static final ThreadLocal<Deque<BlockChangeEventInfo>> BLOCK_STACK = ThreadLocal
        .withInitial(ArrayDeque::new);

    // Pushed in place of a real event for block changes that won't be logged, so HEAD and RETURN stay paired
    // without paying for a stack trace and NBT capture that would be thrown away.
    private static final BlockChangeEventInfo SKIPPED = new BlockChangeEventInfo();

    public void onSetBlockHead(int x, int y, int z, World world) {
        if (!isEnabledByConfig) return;

        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();
        int dimensionID = world.provider.dimensionId;

        // todo log world gen separately?
        if (WorldGenPhaseTracker.isWorldGen() || !canLogHere(x, y, z, dimensionID)) {
            stack.push(SKIPPED);
            return;
        }

        BlockChangeEventInfo e = new BlockChangeEventInfo();
        stack.push(e);

        e.eventID = UUID.randomUUID()
            .toString();
        e.timestamp = System.currentTimeMillis();
        e.stackTraceID = StackTraceUtils.getID(StackTraceUtils.getCallingClassChain()); // TODO PERSISTENCE OF IDS

        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionID = dimensionID;

        // BEFORE state
        Block before = world.getBlock(x, y, z);
        e.beforeBlockID = Block.getIdFromBlock(before);
        e.beforeMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = getPickBlockSafe(before, world, x, y, z);
        if (pick != null && pick.getItem() != null) {
            e.beforePickBlockID = Item.getIdFromItem(pick.getItem());
            e.beforePickBlockMeta = pick.getItemDamage();
        } else {
            e.beforePickBlockID = e.beforeBlockID;
            e.beforePickBlockMeta = e.beforeMetadata;
        }

        e.beforeEncodedNBT = getEncodedTileEntityNBT(world, x, y, z, BlockChangeLogger.isLogNBTEnabled());
    }

    public void onSetBlockReturn(int x, int y, int z, World world, CallbackInfoReturnable<Boolean> cir) {
        if (!isEnabledByConfig) return;

        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();

        if (stack.isEmpty()) {
            LOG.error("[BLOCK CHANGE LOGGER CRITICAL ERROR] RETURN without matching HEAD", new Exception());
            throw new IllegalStateException();
        }

        BlockChangeEventInfo e = stack.pop();

        // Not logged here, or the placement failed.
        if (e == SKIPPED || !cir.getReturnValue()) {
            return;
        }

        fillAfterState(e, world, x, y, z);
        recordSetBlock(e.x, e.y, e.z, e, world);
    }

    /**
     * Logs a block change that did not go through {@code Chunk.func_150807_a}, so the mixin never saw it, e.g. a block
     * written straight into the chunk's storage. Call this after the change, with what was there before: the old block,
     * its metadata, and its tile entity's NBT if it had one.
     */
    public void logBlockChange(World world, int x, int y, int z, Block before, int beforeMetadata,
        @Nullable NBTTagCompound beforeTileEntityNBT) {
        if (!isEnabledByConfig) return;

        int dimensionID = world.provider.dimensionId;
        if (WorldGenPhaseTracker.isWorldGen() || !canLogHere(x, y, z, dimensionID)) return;

        BlockChangeEventInfo e = new BlockChangeEventInfo();
        e.eventID = UUID.randomUUID()
            .toString();
        e.timestamp = System.currentTimeMillis();
        e.stackTraceID = StackTraceUtils.getID(StackTraceUtils.getCallingClassChain());

        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionID = dimensionID;

        // BEFORE state, as reported. The world already holds the new block, so the pick block can only come from the
        // metadata rather than from the block in the world.
        e.beforeBlockID = Block.getIdFromBlock(before);
        e.beforeMetadata = beforeMetadata;

        Item beforeItem = Item.getItemFromBlock(before);
        if (beforeItem != null) {
            e.beforePickBlockID = Item.getIdFromItem(beforeItem);
            e.beforePickBlockMeta = before.damageDropped(beforeMetadata);
        } else {
            e.beforePickBlockID = e.beforeBlockID;
            e.beforePickBlockMeta = e.beforeMetadata;
        }

        if (beforeTileEntityNBT == null) {
            e.beforeEncodedNBT = NO_NBT;
        } else if (logNBT) {
            e.beforeEncodedNBT = encodeToString(beforeTileEntityNBT);
        } else {
            e.beforeEncodedNBT = NBT_DISABLED;
        }

        fillAfterState(e, world, x, y, z);
        recordSetBlock(x, y, z, e, world);
    }

    private static void fillAfterState(BlockChangeEventInfo e, World world, int x, int y, int z) {
        Block after = world.getBlock(x, y, z);
        e.afterBlockID = Block.getIdFromBlock(after);
        e.afterMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = getPickBlockSafe(after, world, x, y, z);
        if (pick != null && pick.getItem() != null) {
            e.afterPickBlockID = Item.getIdFromItem(pick.getItem());
            e.afterPickBlockMeta = pick.getItemDamage();
        } else {
            e.afterPickBlockID = e.afterBlockID;
            e.afterPickBlockMeta = e.afterMetadata;
        }

        e.afterEncodedNBT = getEncodedTileEntityNBT(world, x, y, z, BlockChangeLogger.isLogNBTEnabled());
    }

    private void recordSetBlock(double x, double y, double z, BlockChangeEventInfo eventInfo, World world) {
        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        double closestDistance = -1;

        if (closestPlayer != null) {
            closestDistance = closestPlayer.getDistance(x, y, z);
        }

        eventInfo.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        eventInfo.closestPlayerDistance = closestDistance;

        queueEventInfo(eventInfo);
    }

}
