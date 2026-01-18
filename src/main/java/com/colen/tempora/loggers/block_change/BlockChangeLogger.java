package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.ChatUtils.createHoverableClickable;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.RenderingUtils.CLIENT_EVENT_RENDER_DISTANCE;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.block_change.region_registry.BlockChangeRegionRegistry;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.UndoResponse;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// todo look into logging world gen and marking that separately, such that you can use a useful regen command to restore
// todo look into flowers not reverting/saving properly.
// the state of the world.
public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }

    private static boolean globalBlockChangeLogging;

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
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getLoggerName(), false, """
            If true, overrides all custom regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);

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

    public void onSetBlockHead(int x, int y, int z, World world) {

        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();

        BlockChangeEventInfo e = new BlockChangeEventInfo();
        e.eventID = UUID.randomUUID()
            .toString();
        e.timestamp = System.currentTimeMillis();
        e.stackTrace = GenericUtils.getCallingClassChain();
        stack.push(e);

        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionID = world.provider.dimensionId;

        e.isWorldGen = WorldGenPhaseTracker.isWorldGen();

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

        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();

        if (stack.isEmpty()) {
            LOG.error("[BLOCK CHANGE LOGGER CRITICAL ERROR] RETURN without matching HEAD", new Exception());
            throw new IllegalStateException();
        }

        BlockChangeEventInfo e = stack.pop();

        // If placement failed, mark as no-op and move on
        if (!cir.getReturnValue()) {
            return;
        }

        // AFTER state
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

        recordSetBlock(e.x, e.y, e.z, e, world);
    }

    public static boolean isGlobalBlockChangeLoggingEnabled() {
        return globalBlockChangeLogging;
    }

    private void recordSetBlock(double x, double y, double z, BlockChangeEventInfo eventInfo, World world) {

        if (eventInfo.isWorldGen) return;

        // Only log changes if (x, y, z) is inside a defined region. Unless config has the entire world logging on.
        if (!globalBlockChangeLogging
            && !BlockChangeRegionRegistry.containsBlock(world.provider.dimensionId, (int) x, (int) y, (int) z)) {
            return;
        }

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

    @Override
    public UndoResponse undoEvent(GenericEventInfo eventInfo, EntityPlayer player) {

        IChatComponent eventUUID = createHoverableClickable("[UUID]", eventInfo.eventID);

        // This should never occur.
        if (!(eventInfo instanceof BlockChangeEventInfo bcEventInfo)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation(
                "tempora.undo.unknown_error",
                eventUUID,
                getLoggerName());
            undoResponse.success = false;
            return undoResponse;
        }

        // Handle if logging NBT was off when this event was logged & it is necessary to restore it.
        if (bcEventInfo.beforeEncodedNBT.equals(NBT_DISABLED)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation(
                "tempora.undo.cannot_block_break.nbt_logging_disabled",
                eventUUID);
            undoResponse.success = false;
            return undoResponse;
        }

        int x = (int) bcEventInfo.x;
        int y = (int) bcEventInfo.y;
        int z = (int) bcEventInfo.z;
        int blockID = bcEventInfo.beforeBlockID;
        int meta = bcEventInfo.beforeMetadata;
        int dimID = eventInfo.dimensionID;

        Block block = Block.getBlockById(blockID);
        if (block == null) {
            UndoResponse undoResponse = new UndoResponse();

            undoResponse.message = new ChatComponentTranslation(
                "tempora.undo.cannot_block_break.block_not_found",
                eventUUID,
                bcEventInfo.beforeBlockID,
                bcEventInfo.beforeMetadata);

            undoResponse.success = false;
            return undoResponse;
        }

        World w = MinecraftServer.getServer()
            .worldServerForDimension(dimID);

        // todo use this in other undos.
        // Place silently (no physics or callbacks). Direct edit of chunk data.
        if (!BlockUtils.setBlockNoUpdate(w, x, y, z, block, meta)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.success = false;
            undoResponse.message = new ChatComponentTranslation(
                "tempora.undo.set_block_failed",
                eventUUID,
                getLoggerName());

            return undoResponse;
        }

        if (!bcEventInfo.beforeEncodedNBT.equals(NO_NBT)) {
            try {
                TileEntity te = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(bcEventInfo.beforeEncodedNBT));
                if (te != null) {
                    te.setWorldObj(w);
                    te.xCoord = x;
                    te.yCoord = y;
                    te.zCoord = z;
                    te.validate();
                    w.setTileEntity(x, y, z, te);
                }
            } catch (Exception e) {
                w.setBlockToAir(x, y, z);
                w.removeTileEntity(x, y, z);
                e.printStackTrace();

                UndoResponse undoResponse = new UndoResponse();
                undoResponse.message = new ChatComponentTranslation(
                    "tempora.undo.unknown_error",
                    eventUUID,
                    getLoggerName());
                undoResponse.success = false;

                return undoResponse;
            }
        }

        // Client visual + light refresh now that TE is correct
        w.markBlockForUpdate(x, y, z);

        UndoResponse undoResponse = new UndoResponse();
        undoResponse.success = true;
        undoResponse.message = new ChatComponentTranslation("tempora.undo.success.normal");

        return undoResponse;
    }

    @Override
    public boolean isUndoEnabled() {
        return true;
    }
}
