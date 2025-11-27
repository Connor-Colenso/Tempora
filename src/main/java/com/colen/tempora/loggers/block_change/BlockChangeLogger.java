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
import java.util.Stack;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import com.colen.tempora.utils.EventLoggingHelper;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> implements ISupportsUndo {

    private boolean globalBlockChangeLogging;

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    // It is possible for SetBlock to call other SetBlocks, hence this is required, to untangle nested calls.
    private static final Stack<SetBlockEventInfo> eventInfoStack = new Stack<>();

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

    // TODO Urgent! Look into world gen being logged when it shouldn't. Is causing severe issues with tall grass
    // reverting to air on tempora undo ranged command usage.
    public void onSetBlockHead(int x, int y, int z, Block blockIn, WorldProvider provider) {

        eventInfoStack.add(new SetBlockEventInfo());

        SetBlockEventInfo currentEventInfo = eventInfoStack.peek();
        currentEventInfo.isWorldGen = WorldGenPhaseTracker.isInWorldGen();
        if (currentEventInfo.isWorldGen) return;

        currentEventInfo.beforeBlockID = Block.getIdFromBlock(provider.worldObj.getBlock(x, y, z));
        currentEventInfo.beforeMeta = provider.worldObj.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = getPickBlockSafe(blockIn, provider.worldObj, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.beforePickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.beforePickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place‑block data
            currentEventInfo.beforePickBlockID = currentEventInfo.beforeBlockID;
            currentEventInfo.beforePickBlockMeta = currentEventInfo.beforeMeta;
        }

        // Log NBT.
        currentEventInfo.beforeEncodedNBT = getEncodedTileEntityNBT(
            provider.worldObj,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled());

        currentEventInfo.worldTick = provider.worldObj.getTotalWorldTime();
    }

    public void onSetBlockReturn(int x, int y, int z, Block blockIn, int flags, WorldProvider provider,
        CallbackInfoReturnable<Boolean> cir) {

        SetBlockEventInfo currentEventInfo = eventInfoStack.pop();
        if (currentEventInfo == null) {
            // todo critical error writeup.
            FMLLog.severe("CRITICAL");
        }

        // Block placement failed for some reason. So do not log.
        if (!cir.getReturnValue()) return;
        // We do not log world gen, as it mostly meaningless.
        if (currentEventInfo.isWorldGen) return;

        currentEventInfo.afterBlockID = Block.getIdFromBlock(provider.worldObj.getBlock(x, y, z));
        currentEventInfo.afterMeta = provider.worldObj.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = getPickBlockSafe(blockIn, provider.worldObj, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.afterPickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.afterPickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place‑block data
            currentEventInfo.afterPickBlockID = currentEventInfo.afterBlockID;
            currentEventInfo.afterPickBlockMeta = currentEventInfo.afterMeta;
        }

        // Log NBT.
        currentEventInfo.afterEncodedNBT = getEncodedTileEntityNBT(
            provider.worldObj,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled());

        // Ignore no-ops (same block and metadata)
        if (currentEventInfo.isNoOp()) return;

        // Todo more safety checks like compare x y z.
        // Todo get mod ID.
        if (currentEventInfo.worldTick == provider.worldObj.getTotalWorldTime()) {
            Tempora.blockChangeLogger.recordSetBlock(x, y, z, currentEventInfo, provider, "mod");
        } else {
            FMLLog.severe(
                "[TEMPORA BLOCK LOGGER CRITICAL ERROR]\n" + "World tick mismatch detected during setBlock logging!\n"
                    + "Expected tick: %d | Actual tick: %d\n"
                    + "Dim ID: %d | Pos: (%d,%d,%d)\n"
                    + "Before: %s:%d | After: %s:%d | Flags: %d\n"
                    + "This should NEVER occur.\n"
                    + "Please report this with full logs immediately.",
                currentEventInfo.worldTick,
                provider.worldObj.getTotalWorldTime(),
                provider.dimensionId,
                x,
                y,
                z,
                currentEventInfo.beforeBlockID,
                currentEventInfo.beforeMeta,
                currentEventInfo.afterBlockID,
                currentEventInfo.afterMeta,
                flags);
        }
    }

    private void recordSetBlock(int x, int y, int z, SetBlockEventInfo setBlockEventInfo, WorldProvider worldProvider,
        String modID) {

        // Only log changes if (x, y, z) is inside a defined region
        if (!globalBlockChangeLogging && !RegionRegistry.containsBlock(worldProvider.dimensionId, x, y, z)) {
            return;
        }

        final BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = x;
        queueElement.y = y;
        queueElement.z = z;
        queueElement.dimensionId = worldProvider.dimensionId;
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

        EntityPlayer closestPlayer = worldProvider.worldObj.getClosestPlayer(x, y, z, -1);;
        double closestDistance = -1;

        if (closestPlayer != null) closestDistance = closestPlayer.getDistance(x, y, z);

        queueElement.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

    // private boolean isChunkPopulatedAt(World world, int blockX, int blockZ) {
    // // Convert block coordinates to chunk coordinates
    // int chunkX = blockX >> 4; // divide by 16
    // int chunkZ = blockZ >> 4;
    //
    // // Get the chunk object
    // Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
    //
    // return chunk.isTerrainPopulated;
    // }

    @Override
    public IChatComponent undoEvent(GenericQueueElement queueElement) {
        if (!(queueElement instanceof BlockChangeQueueElement)) return new ChatComponentTranslation("error");

        BlockChangeQueueElement bcqe = (BlockChangeQueueElement) queueElement;

        // NBT existed but was not logged, it is not safe to undo this event.
        if (bcqe.beforeEncodedNBT.equals(NBT_DISABLED))
            return new ChatComponentTranslation("tempora.cannot.block.break.undo.nbt.logging.disabled");

        World w = MinecraftServer.getServer()
            .worldServerForDimension(queueElement.dimensionId);

        Block block = Block.getBlockById(bcqe.beforeBlockID);
        if (block == null) return new ChatComponentTranslation("tempora.cannot.block.break.undo.block.not.found");

        w.setBlock((int) bcqe.x, (int) bcqe.y, (int) bcqe.z, block, bcqe.beforeMetadata, 2);
        // Just to ensure meta is being set right, stops blocks interfering.
        w.setBlockMetadataWithNotify((int) bcqe.x, (int) bcqe.y, (int) bcqe.z, bcqe.beforeMetadata, 2);
        // Block had no NBT.
        if (bcqe.beforeEncodedNBT.equals(NO_NBT)) return new ChatComponentTranslation("tempora.undo.success");

        try {
            TileEntity tileEntity = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(bcqe.beforeEncodedNBT));
            w.setTileEntity((int) bcqe.x, (int) bcqe.y, (int) bcqe.z, tileEntity);
        } catch (Exception e) {
            // Erase the block. Try stop world state having issues.
            w.setBlockToAir((int) bcqe.x, (int) bcqe.y, (int) bcqe.z);

            e.printStackTrace();
            return new ChatComponentTranslation("tempora.undo.block.break.unknown.error");
        }

        return new ChatComponentTranslation("tempora.undo.success");
    }
}
