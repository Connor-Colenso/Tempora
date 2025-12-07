package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.TemporaUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.EventLoggingHelper;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// todo look into logging world gen and marking that separately, such that you can use a useful regen command to restore the state of the world.
public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> implements ISupportsUndo {

    private boolean globalBlockChangeLogging;

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    /**
     * Track nested block writes per thread.
     * We only log when depth goes from 0 -> 1 -> 0.
     */
    private static final ThreadLocal<Integer> depth =
        ThreadLocal.withInitial(() -> 0);

    private static final ThreadLocal<SetBlockEventInfo> currentEvent =
        new ThreadLocal<>();

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.BlockChangeLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        List<BlockChangeQueueElement> sortedList = getSortedLatestEventsByDistance(eventsToRenderInWorld, e);

        for (BlockChangeQueueElement bcqe : sortedList) {
            RenderingUtils.renderBlockWithLogging(
                e,
                bcqe,
                bcqe.beforeBlockID,
                bcqe.beforeMetadata,
                bcqe.beforeEncodedNBT,
                bcqe.closestPlayerUUID,
                getLoggerType());
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("beforeBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("afterBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("stackTrace", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getSQLTableName(), false, """
            If true, overrides all custom regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);

        logNBT = config.getBoolean(
            "logNBT",
            getSQLTableName(),
            false,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    public void threadedSaveEvents(List<BlockChangeQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (beforeBlockID, beforeMetadata, beforePickBlockID, beforePickBlockMeta, beforeEncodedNBT, afterBlockID, afterMetadata, afterPickBlockID, afterPickBlockMeta, afterEncodedNBT, stackTrace, closestPlayerUUID, closestPlayerDistance, eventID, x, y, z, dimensionID, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (BlockChangeQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setInt(index++, queueElement.beforeBlockID);
                pstmt.setInt(index++, queueElement.beforeMetadata);
                pstmt.setInt(index++, queueElement.beforePickBlockID);
                pstmt.setInt(index++, queueElement.beforePickBlockMeta);
                pstmt.setString(index++, queueElement.beforeEncodedNBT);

                pstmt.setInt(index++, queueElement.afterBlockID);
                pstmt.setInt(index++, queueElement.afterMetadata);
                pstmt.setInt(index++, queueElement.afterPickBlockID);
                pstmt.setInt(index++, queueElement.afterPickBlockMeta);
                pstmt.setString(index++, queueElement.afterEncodedNBT);

                pstmt.setString(index++, queueElement.stackTrace);
                pstmt.setString(index++, queueElement.closestPlayerUUID);
                pstmt.setDouble(index++, queueElement.closestPlayerDistance);

                EventLoggingHelper.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.beforeBlockID = resultSet.getInt("beforeBlockID");
            queueElement.beforeMetadata = resultSet.getInt("beforeMetadata");
            queueElement.beforePickBlockID = resultSet.getInt("beforePickBlockID");
            queueElement.beforePickBlockMeta = resultSet.getInt("beforePickBlockMeta");
            queueElement.beforeEncodedNBT = resultSet.getString("beforeEncodedNBT");

            queueElement.afterBlockID = resultSet.getInt("afterBlockID");
            queueElement.afterMetadata = resultSet.getInt("afterMetadata");
            queueElement.afterPickBlockID = resultSet.getInt("afterPickBlockID");
            queueElement.afterPickBlockMeta = resultSet.getInt("afterPickBlockMeta");
            queueElement.afterEncodedNBT = resultSet.getString("afterEncodedNBT");

            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.closestPlayerUUID = PlayerUtils.UUIDToName(resultSet.getString("closestPlayerUUID"));
            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");

            events.add(queueElement);
        }
        return events;
    }

    /**
     * Called at HEAD of Chunk.func_150807_a
     */
    public void onSetBlockHead(int x, int y, int z, World world) {

        int d = depth.get();
        depth.set(d + 1);

        // Only capture info for the outermost mutation in this call stack
        if (d != 0) {
            return;
        }

        SetBlockEventInfo currentEventInfo = new SetBlockEventInfo();
        currentEvent.set(currentEventInfo);

        currentEventInfo.isWorldGen = WorldGenPhaseTracker.isWorldGen();
        if (currentEventInfo.isWorldGen) {
            return;
        }

        currentEventInfo.beforeBlockID = Block.getIdFromBlock(world.getBlock(x, y, z));
        currentEventInfo.beforeMeta = world.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = world.getBlock(x, y, z).getPickBlock(null, world, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.beforePickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.beforePickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place-block data
            currentEventInfo.beforePickBlockID = currentEventInfo.beforeBlockID;
            currentEventInfo.beforePickBlockMeta = currentEventInfo.beforeMeta;
        }

        // Log NBT.
        currentEventInfo.beforeEncodedNBT = getEncodedTileEntityNBT(
            world,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled());

        currentEventInfo.worldTick = world.getTotalWorldTime();
    }

    /**
     * Called at RETURN of Chunk.func_150807_a
     */
    public void onSetBlockReturn(int x, int y, int z, World world,
                                 CallbackInfoReturnable<Boolean> cir) {

        int d = depth.get() - 1;
        depth.set(d);

        // Only act when unwinding the outermost call
        if (d != 0) {
            return;
        }

        SetBlockEventInfo currentEventInfo = currentEvent.get();
        currentEvent.remove();

        if (currentEventInfo == null) {
            FMLLog.severe(
                "[TEMPORA BLOCK LOGGER CRITICAL ERROR]\n"
                    + "SetBlock return hook encountered a null SetBlockEventInfo at outermost depth!\n"
                    + "This indicates that onSetBlockReturn was called without a matching onSetBlockHead,\n"
                    + "or that the event tracking has become desynchronised.\n");
            new IllegalStateException("Tempora SetBlockEventInfo missing at outermost depth").printStackTrace();
            return;
        }

        // Block placement failed for some reason. So do not log.
        if (!cir.getReturnValue()) return;
        // We do not log world gen, as it is mostly meaningless.
        if (currentEventInfo.isWorldGen) return;

        currentEventInfo.afterBlockID = Block.getIdFromBlock(world.getBlock(x, y, z));
        currentEventInfo.afterMeta = world.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = world.getBlock(x, y, z).getPickBlock(null, world, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.afterPickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.afterPickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place-block data
            currentEventInfo.afterPickBlockID = currentEventInfo.afterBlockID;
            currentEventInfo.afterPickBlockMeta = currentEventInfo.afterMeta;
        }

        // Log NBT.
        currentEventInfo.afterEncodedNBT = getEncodedTileEntityNBT(
            world,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled()); // todo investigate chest breaking and not saving nbt.

        // Ignore no-ops (same block and metadata)
        if (currentEventInfo.isNoOp()) return;

        // Todo: more safety checks like compare x y z.
        // Todo: get mod ID.
        if (currentEventInfo.worldTick == world.getTotalWorldTime()) {
            Tempora.blockChangeLogger.recordSetBlock(x, y, z, currentEventInfo, world, "mod");
        } else {
            FMLLog.severe(
                "[TEMPORA BLOCK LOGGER CRITICAL ERROR]\n"
                    + "World tick mismatch detected during setBlock logging!\n"
                    + "Expected tick: %d | Actual tick: %d\n"
                    + "Dim ID: %d | Pos: (%d,%d,%d)\n"
                    + "Before: %s:%d | After: %s:%d\n"
                    + "This should NEVER occur.\n"
                    + "Please report this with full logs immediately.",
                currentEventInfo.worldTick,
                world.getTotalWorldTime(),
                world.provider.dimensionId,
                x,
                y,
                z,
                currentEventInfo.beforeBlockID,
                currentEventInfo.beforeMeta,
                currentEventInfo.afterBlockID,
                currentEventInfo.afterMeta);
        }
    }

    private void recordSetBlock(int x, int y, int z, SetBlockEventInfo setBlockEventInfo, World world,
                        String modID) {

        // Only log changes if (x, y, z) is inside a defined region
        if (!globalBlockChangeLogging && !RegionRegistry.containsBlock(world.provider.dimensionId, x, y, z)) {
            return;
        }

        final BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.eventID = UUID.randomUUID().toString();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.stackTrace = modID + " : " + GenericUtils.getCallingClassChain();

        queueElement.beforeBlockID = setBlockEventInfo.beforeBlockID;
        queueElement.beforeMetadata = setBlockEventInfo.beforeMeta;
        queueElement.beforePickBlockID = setBlockEventInfo.beforePickBlockID;
        queueElement.beforePickBlockMeta = setBlockEventInfo.beforePickBlockMeta;
        queueElement.beforeEncodedNBT = setBlockEventInfo.beforeEncodedNBT;

        queueElement.afterBlockID = setBlockEventInfo.afterBlockID;
        queueElement.afterMetadata = setBlockEventInfo.afterMeta;
        queueElement.afterPickBlockID = setBlockEventInfo.afterPickBlockID;
        queueElement.afterPickBlockMeta = setBlockEventInfo.afterPickBlockMeta;
        queueElement.afterEncodedNBT = setBlockEventInfo.afterEncodedNBT;

        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        double closestDistance = -1;

        if (closestPlayer != null) {
            closestDistance = closestPlayer.getDistance(x, y, z);
        }

        queueElement.closestPlayerUUID = closestPlayer != null
            ? closestPlayer.getUniqueID().toString()
            : UNKNOWN_PLAYER_NAME;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

    @Override
    public IChatComponent undoEvent(GenericQueueElement queueElement) {
        if (!(queueElement instanceof BlockChangeQueueElement))
            return new ChatComponentTranslation("error");

        BlockChangeQueueElement bcqe = (BlockChangeQueueElement) queueElement;

        if (bcqe.beforeEncodedNBT.equals(NBT_DISABLED))
            return new ChatComponentTranslation("tempora.cannot.block.break.undo.nbt.logging.disabled");

        World w = MinecraftServer.getServer().worldServerForDimension(queueElement.dimensionId);
        Block block = Block.getBlockById(bcqe.beforeBlockID);
        if (block == null)
            return new ChatComponentTranslation("tempora.cannot.block.break.undo.block.not.found");

        int x = (int) bcqe.x;
        int y = (int) bcqe.y;
        int z = (int) bcqe.z;
        int meta = bcqe.beforeMetadata;

        // Place silently (no physics or callbacks)
        BlockUtils.setBlockNoUpdate(w, x, y, z, block, meta); // todo use this in other undos.

        if (!bcqe.beforeEncodedNBT.equals(NO_NBT)) {
            try {
                TileEntity te = TileEntity.createAndLoadEntity(
                    NBTUtils.decodeFromString(bcqe.beforeEncodedNBT));
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
                return new ChatComponentTranslation("tempora.undo.block.break.unknown.error");
            }
        }

        // Client visual + light refresh now that TE is correct
        w.markBlockForUpdate(x, y, z);

        return new ChatComponentTranslation("tempora.undo.success");
    }
}
